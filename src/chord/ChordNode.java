package chord;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;


public class ChordNode {
    private int id;                     // The peer's unique identifier
    private int m;                      // Number of bits of the addressing space
    private InetSocketAddress address;  // The peer's network address;
    private final HashMap<Integer, ChordNode> fingerTable = new HashMap<>();
    private ChordNode predecessor;
    private ChordNode successor;

    public ChordNode(InetSocketAddress address, int m) {
        id = Math.floorMod(sha1(address.toString()), m);
        this.m = m;
        successor = this;
    }

    public void join(ChordNode node) {
        successor = node.findSuccessor(id);
    }

    public void stabilize() {
        ChordNode x = successor.getPredecessor();

        if (x.id > this.id && x.id < successor.id)
            successor = x;

        successor.notify(this);
    }

    public void notify(ChordNode node) {
        if (predecessor == null || (node.id > predecessor.id && node.id < this.id))
            predecessor = node;
    }

    /**
     * Called periodically. refreshes finger table entries.
     */
    public void fix_fingers() {

    }

    public ChordNode closestPrecidingNode(int id) {
        // Search the local table for the highest predecessor of id
        for (int i = m; i >= 1; i--)
            if (fingerTable.get(i).id > this.id && fingerTable.get(i).id < id)
                return fingerTable.get(i);

        return this;
    }

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
