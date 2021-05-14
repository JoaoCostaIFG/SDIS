package chord;

import message.Message;
import peer.Peer;
import sender.MessageHandler;
import sender.SockThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class ChordNode {
    private final int id;                        // The peer's unique identifier
    private final InetAddress address;     // The peer's network address;
    private final int port;
    private final List<ChordNode> fingerTable = new ArrayList<>();
    private final MessageHandler messageHandler;
    private int next;
    private ChordNode predecessor;
    private ChordNode successor;
    private SockThread sock;

    public static int m = 127;                         // Number of bits of the addressing space

    public ChordNode(InetAddress address, int port) throws IOException {
        this.address = address;
        this.port = port;
        this.id = Math.floorMod(sha1(address.toString() + port), m);
        this.sock = new SockThread("sock", address, port);
        this.messageHandler = new MessageHandler(this.id, this.sock);
        successor = this;
    }

    public void start() {
        this.sock.start();
    }

    public void stop() {
        this.sock.interrupt();
    }

    public void send(Message message, InetAddress ip, int port) {
        this.sock.send(message, ip, port);
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
