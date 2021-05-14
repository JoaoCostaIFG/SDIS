package sender;

import message.Message;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SockThread implements Runnable {
    private static final int MAX_CONNS = 5;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final String name;
    private final SSLServerSocket serverSocket;
    private final InetAddress ip;
    private final Integer port;
    private MessageHandler handler;

    public SockThread(String name, InetAddress ip, Integer port) throws IOException {
        this.name = name;
        this.ip = ip;
        this.port = port;

        this.serverSocket =
                (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port, MAX_CONNS, ip);
    }

    public String getName() {
        return this.name;
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    public void close() {
        this.threadPool.shutdown();

        try {
            this.serverSocket.close();
        }

        catch (IOException e) {
            e.printStackTrace();
        }
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
            int n;
            SSLSocket socket; // TODO Use threads to accept connections concurrently?
            try {
                socket = (SSLSocket) serverSocket.accept();
            } catch (IOException e) {
                System.err.println("Timed out while waiting for answer (Sock thread) " + this);
                try {
                    serverSocket.close();
                } catch (IOException ioException) {
                    System.err.println("Failed to close socket (ChunkTCP)");
                }
                continue;
            }

            try {
                InputStream socketReader = socket.getInputStream();
                n = socketReader.read(packetData);
            } catch (SocketException e) {
                // happens if the blocking call is interrupted
                break;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            this.threadPool.execute(
                    () -> handler.handleMessage(this.getName(),
                            Arrays.copyOfRange(packetData, 0, n))
            );
        }
    }

    public void send(Message message, InetAddress address, int port) {
        byte[] packetContent = message.getContent();
        System.out.println("Sent: " + message);
        try {
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(address, port);
            OutputStream socketWriter = socket.getOutputStream();
            socketWriter.write(packetContent);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return ip + ":" + port + "\n";
    }
}