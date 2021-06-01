package chord;

import message.Message;
import message.PutChunkMsg;
import message.RemovedMsg;
import sender.MessageHandler;
import sender.Observer;
import sender.SockThread;
import state.State;
import utils.Pair;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.pow;

public class ChordNode implements ChordInterface, Observer {
    private final Integer id;              // The peer's unique identifier
    private final InetAddress address;     // The peer's network address;
    private final int port;
    private final ChordInterface[] fingerTable;
    private final MessageHandler messageHandler;
    private final ChordInterface[] succList;
    private int nextFingerToFix;
    private ChordInterface predecessor;
    public Registry registry;
    private final SockThread sock;

    public static int m = 7; // Number of bits of the addressing space
    private static final int MAX_TRIES = 3;

    public ChordNode(InetAddress address, int port, Registry registry) throws IOException {
        this.address = address;
        this.port = port;
        this.id = ChordNode.genId(address, port);
        this.sock = new SockThread(address, port, this);
        this.messageHandler = new MessageHandler(this.sock, this);

        this.registry = registry;
        this.nextFingerToFix = 0;
        this.fingerTable = new ChordInterface[m];
        this.succList = new ChordInterface[(int) Math.ceil(m / 3.0)];

        // init node as if he was the only one in the network
        this.predecessor = null;
        this.setSuccessor(this);

        ChordInterface stub;
        try {
            stub = (ChordInterface) UnicastRemoteObject.exportObject(this, 0);
            registry.bind(this.id.toString(), stub);
            System.out.println("Registered node with id: " + this.id);
            System.out.println(this);
        } catch (Exception e) {
            System.err.println("Failed setting up the access point for use by chord node.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public int getId() {
        return id;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    /* CHORD */
    @Override
    public ChordInterface getPredecessor() {
        return predecessor;
    }

    @Override
    public ChordInterface getSuccessor() throws RemoteException {
        boolean goneBad = false;

        for (int i = 0; i < this.succList.length; ++i) {
            ChordInterface succ = this.succList[i];
            if (succ == null) break;

            try {
                succ.getId();
                if (goneBad) {
                    this.reconcile(succ);

                    // My successor died, call backup protocol on the chunks i think he was storing
                    this.backupSuccessorChunks();
                }
                return succ;
            } catch (RemoteException ignored) {
                goneBad = true;
                this.succList[i] = null; // node is dead => bye bye
            }
        }

        return null;

        // return this.fingerTable[0];
    }

    @Override
    public ChordInterface[] getSuccessors() throws RemoteException {
        return this.succList;
    }

    private void setSuccessor(ChordInterface n) {
        this.succList[0] = n;
        this.fingerTable[0] = this.succList[0];

        Map<Pair<String, Integer>, Integer> succStoredChunksIds;
        try {
            succStoredChunksIds = n.getStoredChunksIds();
        } catch (RemoteException ignored) {
            System.err.println("Couldn't get stored chunks of my new successor");
            return;
        }
        State.st.replaceSuccChunk(succStoredChunksIds);

        //this.fingerTable[0] = n;
    }

    @Override
    public Map<Pair<String, Integer>, Integer> getStoredChunksIds() throws RemoteException {
        return State.st.getAllStoredChunksId();
    }

    private int getFingerStartId(int i) {
        return (this.id + (int) pow(2, i)) % (int) Math.pow(2, m);
    }

    /**
     * Make the node join a Chord ring, with n as its successor
     */
    public void join(ChordInterface nprime) throws RemoteException {
        this.predecessor = null;
        this.setSuccessor(nprime.findSuccessor(this.getId()));

        // init finger table
        for (int i = 0; i < m; ++i) {
            int fingerStartId = this.getFingerStartId(i);
            this.fingerTable[i] = nprime.findSuccessor(fingerStartId);
        }
    }

    private void reconcile(ChordInterface succ) throws RemoteException {
        ChordInterface[] succSuccessors = succ.getSuccessors();
        this.succList[0] = succ;
        System.arraycopy(succSuccessors, 0, this.succList, 1, this.succList.length - 1);
    }

    /**
     * Called periodically.
     * The node asks the successor about its predecessor, verifies if its immediate successor is consistent,
     * and tells the successor about it
     */
    public void stabilize() throws RemoteException {
        // TODO stack trace no timer do stabilize para ver nos ctrl+c

        // TODO if statement is needed?
        //if (this.predecessor != null) {
        //}

        ChordInterface succ = this.getSuccessor();
        if (succ == null) return; // very bad

        // update predecessor
        ChordInterface me$ = succ.getPredecessor();
        if (me$ != null) { // if pred knows about a succ
            if (ChordNode.inBetween(me$.getId(), this.id, succ.getId())) {
                this.setSuccessor(me$);
                succ = me$;
            }
        }
        succ.notify(this); // notify successor about us

        this.reconcile(succ);
    }

    /**
     * Node n thinks it might be our predecessor.
     */
    @Override
    public void notify(ChordInterface nprime) throws RemoteException {
        int nprimeId = nprime.getId();

        if (this.predecessor == null) {
            this.predecessor = nprime;
        } else {
            try {
                int predecessorId = this.predecessor.getId();
                if (ChordNode.inBetween(nprimeId, predecessorId, this.id))
                    this.predecessor = nprime;
            } catch (RemoteException ignored) {
                // if our predecessor died, we accept the new one
                this.predecessor = nprime;
            }
        }
    }

    /**
     * Called periodically. refreshes finger table entries.
     */
    public void fixFingers() {
        if (nextFingerToFix >= m)
            nextFingerToFix = 0;

        int succId = this.getFingerStartId(nextFingerToFix);
        try {
            // System.out.println("I am " + this.id + " and I'm updating finger " + nextFingerToFix + ", id: " + succId);
            fingerTable[nextFingerToFix] = this.findSuccessor(succId);
            // System.out.println("They tell me it's: " + fingerTable[nextFingerToFix].getId());
        } catch (RemoteException e) {
            fingerTable[nextFingerToFix] = this;
        }
        ++nextFingerToFix;
    }

    /**
     * Ask node n to find the successor of id
     */
    @Override
    public ChordInterface findSuccessor(int id) throws RemoteException {
        ChordInterface nprime = this.findPredecessor(id);
        if (nprime == null) return this;
        return nprime.getSuccessor();
    }

    @Override
    public ChordInterface findPredecessor(int id) throws RemoteException {
        ChordInterface succ = this.getSuccessor();
        if (succ == null) return null;

        if (this.getId() == succ.getId())  // only node in network
            return this;

        ChordInterface ret = this;
        while (!(ChordNode.inBetween(id, ret.getId(), ret.getSuccessor().getId(), false, true))) {
            // System.out.println(id + " E (" + ret.getId() + ", " + ret.getSuccessor().getId() + ")");
            ret = ret.closestPrecedingNode(id);
        }
        return ret;
    }

    private int dist(ChordInterface dst) throws RemoteException {
        int dstId = dst.getId();
        if (dstId >= this.id) return dstId - this.id;
        return dstId + (int) Math.pow(2, m) - this.id;
    }

    // finger table + successor list order from farthest to closest
    private List<ChordInterface> getFullTable() {
        // dedup nodes
        Set<ChordInterface> nodeSet = new HashSet<>();
        for (ChordInterface n : this.fingerTable) {
            if (n == null) continue;
            try {
                // test if is alive
                n.getId();
                nodeSet.add(n);
            } catch (RemoteException ignored) {
            }
        }
        for (ChordInterface n : this.succList) {
            if (n == null) continue;
            try {
                // test if is alive
                n.getId();
                nodeSet.add(n);
            } catch (RemoteException ignored) {
            }
        }

        List<ChordInterface> ret = new ArrayList<>(nodeSet);
        // TODO race condition
        ret.sort((arg0, arg1) -> {
            try {
                return this.dist(arg1) - this.dist(arg0);
            } catch (RemoteException ignored) { // Compare someone who died => d = 0
            }
            return 0;
        });

        return ret;
    }

    /**
     * Search the local table for the highest predecessor of id
     */
    @Override
    public ChordInterface closestPrecedingNode(int id) throws RemoteException {
        List<ChordInterface> nodes = this.getFullTable();

        for (ChordInterface n : nodes) {
            int succId;
            try { // is up
                succId = n.getId();
            } catch (RemoteException e) {
                continue;
            }

            // is it is between us and them, it's the closest preceding node
            if (ChordNode.inBetween(succId, this.id, id))
                return n;
        }

        return this;
    }

    /**
     * Called periodically. checks whether predecessor has failed.
     */
    public void checkPredecessor() {
        if (predecessor != null) {
            try {
                this.predecessor.getId();
            } catch (RemoteException e) {
                this.predecessor = null;
            }
        }
    }

    /**
     * Calculates SHA1 Algorithm and hashes string to an integer
     */
    public static int genId(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(s.getBytes());
            ByteBuffer wrapped = ByteBuffer.wrap(hash);
            int div = (int) Math.floor(pow(2, ChordNode.m));
            return Math.floorMod(wrapped.getInt(), div);
        } catch (NoSuchAlgorithmException ignored) {
        }
        return -1;
    }

    public static int genId(InetAddress address, int port) {
        return ChordNode.genId(address.getHostAddress() + ":" + port);
    }

    static boolean inBetween(int num, int lh, int rh, boolean closedLeft, boolean closedRight) {
        if (closedLeft && num == lh) return true;
        if (closedRight && num == rh) return true;

        if (num == lh || num == rh)
            return false;
        if (lh == rh)
            return true;
        if (num > lh && num < rh)
            return true;
        return lh > rh && (num < rh || num > lh);
    }

    static boolean inBetween(int num, int lf, int rh) {
        return ChordNode.inBetween(num, lf, rh, false, false);
    }

    private void backupSuccessorChunks() {
        synchronized (State.st) {
            if (State.st.hasSuccChunks()) {
                for (var entry : State.st.getSuccChunksIds().entrySet()) {
                    System.out.println("\tMy succ died");
                    String fileId = entry.getKey().p1;
                    Integer chunkNo = entry.getKey().p2, chunkId = entry.getValue();
                    // We want to handle instead of sending because we can have the file
                    this.messageHandler.handleMessage(new RemovedMsg(fileId, chunkNo, chunkId, chunkId, false));
                }
                State.st.clearSuccChunks();
            }
        }
    }

    /* TCP */
    public void start() {
        this.sock.start();
    }

    public void stop() {
        this.sock.interrupt();
        this.sock.close();
    }

    public boolean messageIsForUs(Message message) {
        if (this.predecessor == null) // Assume message is for us if our predecessor bye
            return true;

        try {
            return message.destAddrKnown() || // the message was sent directly and without hops for us
                    ChordNode.inBetween(message.getDestId(), this.predecessor.getId(), this.id, false, true);
        } catch (RemoteException e) {
            return true; // Assume that message is for us if chord ring is broken temporarily
        }
    }

    private void sendToNode(Message message) {
        ChordInterface nextHopDest = null;
        try {
            nextHopDest = closestPrecedingNode(message.getDestId());
        } catch (RemoteException e) {
            System.err.println("Could not find successor for message " + message + ". Message not sent.");
            e.printStackTrace();
        }
        assert nextHopDest != null;
        try {
            if (nextHopDest == this)
                nextHopDest = getSuccessor();
            message.setDest(nextHopDest);
            message.addToPath(nextHopDest.getId());
        } catch (RemoteException e) { // TODO Max num of tries?
            System.err.println("Could connect to chosen next hop dest : TODO Max tries with timeout " + message);
            e.printStackTrace();
        }

        this.sock.send(message);
    }

    @Override
    public void handle(Message message) {
        System.out.print("\tReceived: " + message + " - ");
        // Message is for us
        if (this.messageIsForUs(message)) {
            System.out.println("Handling\n");
            messageHandler.handleMessage(message);
        } else { // message isn't for us
            System.out.println("Resending\n");
            this.sendToNode(message); // resend it through the chord ring
        }
    }

    public void send(Message message) {
        if (this.messageIsForUs(message)) {
            System.out.println("\tNot sending message (its for me): " + message + "\n");
            messageHandler.handleMessage(message);
        } else { // message isn't for us
            System.out.println("Sending (ReHopping): " + message + "\n");
            this.sendToNode(message); // resend it through the chord ring
        }
    }

    public void sendDirectly(Message message, InetAddress address, int port) {
        System.out.println("Sending Directly: " + message + "\n");
        message.setDest(address, port);
        // We don't need the chord ring to hop to the dest so we set it to null
        message.setDestId(null);
        this.sock.send(message);
    }

    public void sendDirectly(Message message, ChordInterface node) throws RemoteException {
        this.sendDirectly(message, node.getAddress(), node.getPort());
    }

    public void sendToSucc(Message message) {
        ChordNode node = this;

        Timer timer = new Timer();
        TimerTask task = new java.util.TimerTask() {
            private int count = 0;

            @Override
            public void run() {
                try {
                    node.sendDirectly(message, node.getSuccessor());
                    timer.cancel();
                } catch (RemoteException e) {
                    System.out.println("FAILED ONCE");
                    ++count;
                    if (count == MAX_TRIES)
                        timer.cancel();
                }
            }
        };

        timer.scheduleAtFixedRate(
                task,
                100,
                100
        );
    }

    public void addChunkFuture(String fileId, int currChunk, CompletableFuture<byte[]> fut) {
        this.messageHandler.addChunkFuture(fileId, currChunk, fut);
    }

    public void removeAllChunkFuture(String fileId) {
        this.messageHandler.removeAllChunkFuture(fileId);
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder("FingerTable:\n");
        for (int i = 0; i < m; ++i) {
            res.append("\t").append(i).append("(").append(this.getFingerStartId(i)).append("): ");
            try {
                res.append(this.fingerTable[i].getId()).append("\n");
            } catch (RemoteException | NullPointerException e) {
                res.append("Cant get to node\n");
            }
        }

        res.append("Succ list:\n");
        for (ChordInterface n : this.succList) {
            res.append("\t");
            if (n == null) {
                res.append("null");
            } else {
                try {
                    res.append(n.getId());
                } catch (RemoteException e) {
                    res.append("dead");
                }
            }
            res.append("\n");
        }

        try {
            res.append("Succ: ").append(this.getSuccessor().getId()).append("\n");
        } catch (RemoteException e) {
            res.append("Succ: Can't get succ\n");
        }
        if (predecessor != null) {
            try {
                res.append("Pred: ").append(predecessor.getId()).append("\n");
            } catch (RemoteException e) {
                res.append("Pred: Can't get pred\n");
            }
        } else res.append("Pred: Can't get pred\n");

        return "Chord id: " + id + "\n" + res + "\n"
                + "Sock: " + this.sock;
    }
}