package chord;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.lang.Math.pow;

public class ChordNode implements ChordInterface {
    private final Integer id;              // The peer's unique identifier
    private final InetAddress address;     // The peer's network address;
    private final int port;
    private final ChordInterface[] fingerTable;
    private int nextFingerToFix;
    private ChordInterface predecessor;
    private ChordInterface successor;
    public Registry registry = null;

    public static int m = 7; // Number of bits of the addressing space

    public ChordNode(InetAddress address, int port, Registry registry) {
        this.address = address;
        this.port = port;
        this.id = ChordNode.genId(address, port);
        this.registry = registry;
        this.nextFingerToFix = 0;
        this.fingerTable = new ChordInterface[m];

        // init node as if he was the only one in the network
        for (int i = 0; i < m; ++i)
            this.fingerTable[i] = this;
        this.predecessor = this.successor = this;

        ChordInterface stub;
        try {
            stub = (ChordInterface) UnicastRemoteObject.exportObject(this, 0);
            registry.bind(this.id.toString(), stub);
            System.out.println("Registered node with id: " + this.id);
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

    public String getAddress() {
        return address.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    @Override
    public ChordInterface getPredecessor() {
        return predecessor;
    }

    @Override
    public ChordInterface getSuccessor() throws RemoteException {
        return successor;
    }

    private int getFingerStartId(int i) {
        return (this.id + (int) pow(2, i)) % (int) Math.pow(2, m);
    }

    /**
     * Make the node join a Chord ring, with n as its successor
     */
    public void join(ChordInterface nprime) throws RemoteException {
        this.successor = nprime.findSuccessor(this.getId());
        this.predecessor = this.successor.getPredecessor();
        for (int i = 0; i < m; ++i) {
            int fingerStartId = this.getFingerStartId(i);
            this.fingerTable[i] = nprime.findSuccessor(fingerStartId);
        }
    }

    /**
     * Called periodically.
     * The node asks the successor about its predecessor, verifies if its immediate successor is consistent,
     * and tells the successor about it
     */
    public void stabilize() throws RemoteException {
        // TODO try catch with list of succs

        // update predecessor
        /*
        if (this.predecessor != null) {
            me$ = this.predecessor.getSuccessor();
            if (me$ != null) { // if pred knows about a succ
                if (ChordNode.inBetween(me$.getId(), this.predecessor.getId(), this.id))
                    this.predecessor = me$;
            }
        }
        */

        // update successor
        ChordInterface me$ = this.successor.getPredecessor();
        if (me$ != null) { // if succ knows about a pred
            if (ChordNode.inBetween(me$.getId(), this.id, this.successor.getId())) {
                this.successor = me$;
            }
        }
        successor.notify(this);
    }

    /**
     * Node n thinks it might be our predecessor.
     */
    @Override
    public void notify(ChordInterface nprime) throws RemoteException {
        int nprimeId = nprime.getId();

        /*
        if (ChordNode.inBetween(nprimeId, this.id, this.successor.getId())) {
            this.fingerTable[0] = this.successor = nprime;
            // TODO bootstrap(nprime) ?
        }
        */

        if (this.predecessor == null ||
                ChordNode.inBetween(nprimeId, this.predecessor.getId(), this.id)) {
            this.predecessor = nprime;
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
            System.out.println("I am " + this.id + " and I'm updating finger " + nextFingerToFix + ", id: " + succId);
            fingerTable[nextFingerToFix] = findSuccessor(succId);
            System.out.println("They tell me it's: " + fingerTable[nextFingerToFix].getId());
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
        return nprime.getSuccessor();
    }

    @Override
    public ChordInterface findPredecessor(int id) throws RemoteException {
        if (this.getId() == this.successor.getId())  // only node in network
            return this;

        ChordInterface ret = this;
        while (!(ChordNode.inBetween(id, ret.getId(), ret.getSuccessor().getId(), false, true))) {
            ret = ret.closestPrecedingFinger(id);
        }
        return ret;
    }

    /**
     * Search the local table for the highest predecessor of id
     */
    @Override
    public ChordInterface closestPrecedingFinger(int id) throws RemoteException {
        for (int i = m - 1; i >= 0; --i) {
            int succId = fingerTable[i].getId();
            if (ChordNode.inBetween(succId, this.id, id))
                return fingerTable[i];
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
        if (num >= lh && num <= rh)
            return true;
        return lh > rh && (num < rh || num < lh);
    }

    static boolean inBetween(int num, int lf, int rh) {
        return ChordNode.inBetween(num, lf, rh, false, false);
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder("FingerTable:\n");
        for (ChordInterface n : this.fingerTable) {
            try {
                res.append("\t").append(n.getId()).append("\n");
            } catch (RemoteException e) {
                res.append("\t" + "Cant get to node\n");
            }
        }
        try {
            res.append("Succ: ").append(successor.getId()).append("\n");
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

        return "Chord id: " + id + "\n" + res;
    }
}
