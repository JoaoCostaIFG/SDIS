package chord;

import message.Message;
import message.chord.GetSuccMsg;
import sender.MessageHandler;
import sender.Observer;
import sender.SockThread;

import java.io.IOException;
import java.net.InetAddress;

public class ChordController implements Observer {
    private final MessageHandler messageHandler;
    private SockThread sock;
    private ChordNode node;

    public ChordController(InetAddress address, int port) throws IOException {
        this.node = new ChordNode(address, port);
        this.sock = new SockThread("sock", address, port);
        this.messageHandler = new MessageHandler(this.node.getId(), this.sock);
        this.messageHandler.addObserver(this);
    }

    public ChordNode getNode() {
        return node;
    }

    @Override
    public void notify(Message message) {
        if (message.getClass() == GetSuccMsg.class) {
            GetSuccMsg getSuccMsg = (GetSuccMsg) message;
            ChordNode successor = node.findSuccessor(getSuccMsg.getNodeId());
            if (successor == null) {

            }
        }
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

    @Override
    public String toString() {
        return this.node.toString() +
                "\n" + sock.toString();
    }
}
