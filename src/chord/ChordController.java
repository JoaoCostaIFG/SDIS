package chord;

import message.Message;
import sender.MessageHandler;
import sender.Observer;
import sender.SockThread;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.concurrent.CompletableFuture;

public class ChordController implements Observer {
    private final InetAddress address;     // The peer's network address;
    private final int port;
    private final MessageHandler messageHandler;
    private final SockThread sock;
    private final ChordNode chordNode;

    public ChordController(InetAddress address, int port, Registry registry) throws IOException {
        this.address = address;
        this.port = port;
        this.sock = new SockThread(address, port, this);
        this.messageHandler = new MessageHandler(this.sock, this);
        this.chordNode = new ChordNode(address, port, registry, messageHandler);
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public ChordNode getChordNode() {
        return chordNode;
    }

    public void start() {
        this.sock.start();
    }

    public void stop() {
        this.sock.interrupt();
        this.sock.close();
    }

    public int getId() {
        return this.chordNode.getId();
    }

    public void addChunkFuture(String fileId, int currChunk, CompletableFuture<byte[]> fut) {
        this.messageHandler.addChunkFuture(fileId, currChunk, fut);
    }

    public void removeAllChunkFuture(String fileId) {
        this.messageHandler.removeAllChunkFuture(fileId);
    }

    public void join(ChordInterface node) throws RemoteException {
        this.chordNode.join(node);
    }

    /* SEND/RECEIVE */
    @Override
    public void handle(Message message) {
        System.out.print("\tReceived: " + message + " - ");
        // Message is for us
        if (this.chordNode.messageIsForUs(message)) {
            System.out.println("Handling\n");
            messageHandler.handleMessage(message);
        } else { // message isn't for us
            System.out.println("Resending\n");
            this.sendToNode(message); // resend it through the chord ring
        }
    }

    public void send(Message message) {
        if (this.chordNode.messageIsForUs(message)) {
            System.out.println("\tNot sending message (its for me): " + message + "\n");
            messageHandler.handleMessage(message);
        } else { // message isn't for us
            System.out.println("Sending (ReHopping): " + message + "\n");
            this.sendToNode(message); // resend it through the chord ring
        }
    }

    private void sendToNode(Message message) {
        ChordInterface nextHopDest = null;
        try {
            nextHopDest = this.chordNode.closestPrecedingNode(message.getDestId());
        } catch (RemoteException e) {
            System.err.println("Could not find successor for message " + message + ". Message not sent.");
            e.printStackTrace();
        }
        assert nextHopDest != null;
        try {
            if (nextHopDest == this.chordNode)
                nextHopDest = this.chordNode.getSuccessor();
            message.setDest(nextHopDest);
            message.addToPath(nextHopDest.getId());
        } catch (RemoteException e) { // TODO Max num of tries?
            System.err.println("Could connect to chosen next hop dest : TODO Max tries with timeout " + message);
            e.printStackTrace();
        }

        this.sock.send(message);
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
        try {
            this.sendDirectly(message, this.chordNode.getSuccessor());
        } catch (RemoteException e) {
            System.out.println("Could not get succ to send him " + message);
        }
    }

    public void sendToPred(Message message) {
        try {
            this.sendDirectly(message, this.chordNode.getPredecessor());
        } catch (RemoteException e) {
            System.err.println("Couldn't send REMOVED message to predecessor");
        }
    }

    @Override
    public String toString() {
        return this.chordNode + "\nSock: " + this.sock;
    }
}
