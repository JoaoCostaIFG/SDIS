package chord;

import peer.Peer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ChordNode {
    private final int id;                        // The peer's unique identifier
    private final InetSocketAddress address;     // The peer's network address;
    private final List<ChordNode> fingerTable = new ArrayList<>();
    private final int port;
    private int next;
    private ChordNode predecessor;
    private ChordNode successor;
    private Peer peer;

    public static int m = 127;                         // Number of bits of the addressing space

    public ChordNode(InetSocketAddress address, int port, int numPeers) {
        this.address = address;
        this.port = port;
        this.id = Math.floorMod(sha1(address.toString() + port), m);
        successor = this;
    }

    /**
     * Make the node join a Chord ring, with n as its successor
     */
    public void join(ChordNode n) {
        successor = n.findSuccessor(id);
    }

    /**
     * Called periodically.
     * The node asks the successor about its predecessor, verifies if its immediate successor is consistent,
     * and tells the successor about it
     */
    public void stabilize() {
        ChordNode x = successor.getPredecessor();

        if (x.id > this.id && x.id < successor.id)
            successor = x;

        successor.notify(this);
    }

    /**
     * Node n thinks it might be our predecessor.
     */
    public void notify(ChordNode n) {
        if (predecessor == null || (n.id > predecessor.id && n.id < this.id))
            predecessor = n;
    }

    /**
     * Called periodically. refreshes finger table entries.
     */
    public void fixFingers() {
        next++;

        if (next > m)
            next = 1;

        fingerTable.set(next, findSuccessor(id + (int) Math.pow(2, next - 1)));
    }

    /**
     * Search the local table for the highest predecessor of id
     */
    public ChordNode closestPrecidingNode(int id) {
        // Search the local table for the highest predecessor of id
        for (int i = m; i >= 1; i--)
            if (fingerTable.get(i).id > this.id && fingerTable.get(i).id < id)
                return fingerTable.get(i);

        return this;
    }

    /**
     * Ask node n to find the successor of id
     */
    public ChordNode findSuccessor(int id) {
        if (id > this.id && id < successor.id)
            return successor;

        else
            // Forward the query around the circle
            return closestPrecidingNode(id).findSuccessor(id);
    }

    /**
     * Called periodically. checks whether predecessor has failed.
     */
    public void checkPredecessor() {

    }

    public void lookup() {

    }

    public ChordNode getPredecessor() {
        return predecessor;
    }

    /**
     * Calculates SHA1 Algorithm and hashes string to an integer
     */
    public static int sha1(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            byte[] hash = digest.digest(s.getBytes());
            ByteBuffer wrapped = ByteBuffer.wrap(hash);

            return wrapped.getInt();
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
