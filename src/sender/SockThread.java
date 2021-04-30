package sender;

import message.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SockThread implements Runnable {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final String name;
    private final MulticastSocket sock;
    private final InetAddress group;
    private final Integer port;
    private boolean inGroup;
    private MessageHandler handler;

    public SockThread(String name, MulticastSocket sock, InetAddress group, Integer port) {
        this.name = name;
        this.sock = sock;
        this.group = group;
        this.port = port;

        this.inGroup = false;
        this.join();
    }

    public String getName() {
        return this.name;
    }

    public void join() {
        if (inGroup) return;
        try {
            this.sock.joinGroup(this.group);
            this.inGroup = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void leave() {
        if (!inGroup) return;
        try {
            this.sock.leaveGroup(this.group);
            this.inGroup = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    public void close() {
        this.leave();
        this.threadPool.shutdown();
        this.sock.close();
    }

    public void interrupt() {
        running.set(false);
    }

    public void start() {
        Thread worker = new Thread(this);
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
                // happens if the blocking call is interrupted
                break;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            this.threadPool.execute(
                    () -> handler.handleMessage(this.getName(),
                            Arrays.copyOfRange(packet.getData(), 0, packet.getLength()))
            );
        }
    }

    public void send(Message message) {
        byte[] packetContent = message.getContent();
        System.out.println("Sent: " + message);
        DatagramPacket packet = new DatagramPacket(packetContent, packetContent.length, group, port);

        // TODO cul sleep maybe
        // for (int i = 0; i < 3; ++i) {
        //     try {
        //         sock.send(packet);
        //     } catch (IOException e) {
        //         try {
        //             Thread.sleep(new Random().nextInt(400 + 1), 0);
        //         } catch (InterruptedException interruptedException) {
        //             break;
        //         }
        //         continue;
        //     }
        //     break;
        // }

        try {
            sock.send(packet);
        } catch (IOException ignored) {
        }
    }

    @Override
    public String toString() {
        return group + ":" + port + "\n";
    }
}
