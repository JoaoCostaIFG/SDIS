package chord;

import message.chord.ChordInterface;

import java.math.BigInteger;
import java.net.BindException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ChordNode implements ChordInterface {
    private final Integer id;                        // The peer's unique identifier
    private final InetAddress address;     // The peer's network address;
    private final int port;
    private final List<ChordInterface> fingerTable = new ArrayList<>();
    private int next;
    private ChordInterface predecessor;
    private ChordInterface successor;
    public Registry registry = null;

    public static int m = 7;                         // Number of bits of the addressing space

    public ChordNode(InetAddress address, int port, Registry registry) {
        this.address = address;
        this.port = port;
        this.id = ChordNode.genId(address, port);
        this.registry = registry;
        successor = this;

        ChordInterface stub;
        try {
            stub = (ChordInterface) UnicastRemoteObject.exportObject(this, 0);
            registry.bind(this.id.toString(), stub);
            System.out.println("Registered node with id: " + this.id);
        }
        catch (Exception e) {
            System.err.println("Failed setting up the access point for use by chord node.");
            e.printStackTrace();
            System.exit(1);
        }
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
        successor = n.findSuccessor(id);
    }

    /**
     * Called periodically.
     * The node asks the successor about its predecessor, verifies if its immediate successor is consistent,
     * and tells the successor about it
     */
    public void stabilize() throws RemoteException {
        ChordInterface me$ = successor.getPredecessor();
        int me$Id = me$.getId();

        if (me$Id > this.id && me$Id < successor.getId())
            successor = me$;

        successor.notify(this);
    }

    /**
     * Node n thinks it might be our predecessor.
     */
    public void notify(ChordInterface n) throws RemoteException {
        int nId = n.getId();
        if (predecessor == null || (nId > predecessor.getId() && nId < this.id))
            predecessor = n;
    }

    /**
     * Called periodically. refreshes finger table entries.
     */
    public void fixFingers() throws RemoteException {
        next++;

        if (next > m)
            next = 1;

        fingerTable.set(next, findSuccessor(id + (int) Math.pow(2, next - 1)));
    }

    /**
     * Search the local table for the highest predecessor of id
     */
    public ChordInterface closestPrecidingNode(int id) throws RemoteException {
        // Search the local table for the highest predecessor of id
        for (int i = m; i >= 1; i--) {
            int succId = fingerTable.get(i).getId();
            if (succId > this.id && succId < id)
                return fingerTable.get(i);
        }

        return this;
    }

    /**
     * Ask node n to find the successor of id
     */
    public ChordInterface findSuccessor(int id) throws RemoteException {
        if ((id > this.id && id <= successor.getId()) || successor == this)
            return successor;
        else { // Forward the query around the circle
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
            int div = (int) Math.floor(Math.pow(2, ChordNode.m));
            return Math.floorMod(wrapped.getInt(), div);
        }
        catch (NoSuchAlgorithmException ignored) { }
        return -1;
    }
    public static int genId(InetAddress address, int port) {
        return ChordNode.genId(address.getHostAddress() + ":" + port);
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder("FingerTable:\n");
        for (ChordInterface n: this.fingerTable) {
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
