import message.Message;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SockThread implements Runnable {
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final MulticastSocket sock;
    private final InetAddress group;
    private final Integer port;
    private MessageHandler handler;

    public SockThread(MulticastSocket sock, InetAddress group, Integer port) {
        this.sock = sock;
        this.group = group;
        this.port = port;
    }

    public void join() {
        try {
            this.sock.joinGroup(this.group);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void leave() {
        try {
            this.sock.leaveGroup(this.group);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    public void close() {
        this.sock.close();
    }

    public void interrupt() {
        running.set(false);
    }

    public void start() {
        worker = new Thread(this);
        worker.start();
    }

    @Override
    public void run() {
        running.set(true);
        while (running.get()) {
            byte[] packetData = new byte[64000 + 1000];
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length);

            try {
                this.sock.receive(packet);
            } catch (SocketException e) {
                // happens is the blocking call is interrupted
                break;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            String received = new String(packet.getData(), 0, packet.getLength());
            this.handler.handleMessage(this, received);
        }
    }

    public void send(Message message) {
        byte[] packetContent = message.getContent();
        System.out.println("Sent: " + message);
        DatagramPacket packet = new DatagramPacket(packetContent, packetContent.length, group, port);
        try {
            sock.send(packet);
        } catch (IOException e) {
            e.printStackTrace(); // TODO Move this maybe, do we need to throw it outside?
        }
    }

    @Override
    public String toString() {
        return group + ":" + port + "\n";
    }
}
