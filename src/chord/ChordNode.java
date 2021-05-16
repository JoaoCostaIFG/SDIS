package chord;

import message.chord.ChordInterface;

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
        // Init finger table
        this.fingerTable = new ChordInterface[m];
        for (int i = 0; i < m; ++i)
            this.fingerTable[i] = this;
        this.successor = this;

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

    static boolean inBetween(int num, int lf, int rh) {
        if (num == lf || num == rh)
            return false;
        if (lf == rh)
            return true;
        if (num >= lf && num <= rh)
            return true;
        return lf > rh && (num < rh || num < lf);
    }

    public int getId() {
        return id;
    }

    public String getAddress() {
        return address.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    /**
     * Make the node join a Chord ring, with n as its successor
     */
    public void join(ChordInterface n) throws RemoteException {
        System.err.println("Finding succ " + n.getId() + " of " + this.id);
        predecessor = null;
        successor = n.findSuccessor(id);
    }

    /**
     * Called periodically.
     * The node asks the successor about its predecessor, verifies if its immediate successor is consistent,
     * and tells the successor about it
     */
    public void stabilize() throws RemoteException {
        // TODO try catch with list of succs
        // System.err.println("Stabilizing");
        ChordInterface me$ = successor.getPredecessor();
        if (me$ != null) { // If succ knows about a pred
            int me$Id = me$.getId();

            if (ChordNode.inBetween(me$Id, this.id, successor.getId()))
                successor = me$;
        }
        successor.notify(this);
    }

    /**
     * Node n thinks it might be our predecessor.
     */
    public void notify(ChordInterface n) throws RemoteException {
        int nId = n.getId();
        // System.err.println("Notified by " + nId);

        if (predecessor == null || ChordNode.inBetween(nId, predecessor.getId(), this.id))
            predecessor = n;
    }

    /**
     * Called periodically. refreshes finger table entries.
     */
    public void fixFingers() {
        if (nextFingerToFix >= m)
            nextFingerToFix = 0;

        int succId = (id + (int) pow(2, nextFingerToFix)) % (int) Math.pow(2, m);
        try {
            fingerTable[nextFingerToFix] = findSuccessor(succId);
        } catch (RemoteException e) {
            fingerTable[nextFingerToFix] = this;
        }
        ++nextFingerToFix;
    }

    /**
     * Search the local table for the highest predecessor of id
     */
    public ChordInterface closestPrecidingNode(int id) throws RemoteException {
        // Search the local table for the highest predecessor of id
        for (int i = m - 1; i >= 0; i--) {
            int succId = fingerTable[i].getId();
            if (ChordNode.inBetween(succId, this.id, id))
                return fingerTable[i];
        }

        return this;
    }

    /**
     * Ask node n to find the successor of id
     */
    public ChordInterface findSuccessor(int id) throws RemoteException {
        int succId = successor.getId();
        if (ChordNode.inBetween(id, this.id, succId) || succId == id) {
            return successor;
        } else { // Forward the query around the circle
            return closestPrecidingNode(id).findSuccessor(id);
        }
    }

    @Override
    public String test(String arg) {
        return arg.toUpperCase();
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

    public void lookup() {

    }

    public ChordInterface getPredecessor() {
        return predecessor;
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
