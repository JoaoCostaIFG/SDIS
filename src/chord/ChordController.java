package chord;

import message.Message;
import message.chord.GetSuccMsg;
import sender.MessageHandler;
import sender.Observer;
import sender.SockThread;

import java.io.IOException;
import java.net.InetAddress;

public class ChordController {
    private SockThread sock;

    public ChordController(InetAddress address, int port) throws IOException {
//        this.messageHandler = new MessageHandler(this.node.getId(), this.sock);
//        this.messageHandler.addObserver(this);
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

//    @Override
//    public String toString() {
//        return this.node.toString() +
//                "\n" + sock.toString();
//    }
}
