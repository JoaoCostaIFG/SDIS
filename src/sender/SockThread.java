package sender;

import message.Message;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
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
    private final ServerSocket serverSocket;
    private final InetAddress ip;
    private final Integer port;
    private MessageHandler handler;

    private SSLContext sslc;
    private SSLEngine clientEngine;         // client Engine
    private ByteBuffer clientIn, clientOut; // read/write side of client Engine
    private SSLEngine serverEngine;         // server Engine
    private ByteBuffer serverIn, serverOut; // read/write side of server Engine
    /*
     * For data transport, this example uses local ByteBuffers.  This
     * isn't really useful, but the purpose of this example is to show
     * SSLEngine concepts, not how to do network transport.
     */
    private ByteBuffer cTOs;    // "reliable" transport client->server
    private ByteBuffer sTOc;    // "reliable" transport server->client

    public SockThread(String name, InetAddress ip, Integer port) throws IOException {
        this.name = name;
        this.ip = ip;
        this.port = port;

        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            KeyStore ts = KeyStore.getInstance("JKS");

            char[] passphrase = "123456".toCharArray();
            ks.load(new FileInputStream("../keys/client.keys"), passphrase);
            ts.load(new FileInputStream("../keys/truststore"), passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ts);

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            this.sslc = sslCtx;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableKeyException | KeyManagementException e) {
            e.printStackTrace();
        }

        this.serverSocket = new ServerSocket(port, MAX_CONNS, ip);
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
        } catch (IOException e) {
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
            // SSLSocket socket; TODO Use threads to accept connections concurrently?
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                System.err.println("Timed out while waiting for answer (Sock thread) " + this);
                try {
                    serverSocket.close();
                } catch (IOException ioException) {
                    System.err.println("Failed to close socket (ChunkTCP)");
                }
                continue;
            }

            Object obj;
            try {
                obj = new ObjectInputStream(socket.getInputStream()).readObject();
            } catch (SocketException e) {
                // happens if the blocking call is interrupted
                continue;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            this.threadPool.execute(
                    () -> {
                        assert obj != null;
                        handler.handleMessage((Message) obj);
                    });
        }
    }

    public void send(Message message) {
        System.out.println("Sent: " + message);
        try {
            InetAddress address = message.getDestAddress();
            int port = message.getDestPort();
            Socket socket = new Socket(address, port);
            new ObjectOutputStream(socket.getOutputStream()).writeObject(message);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return ip + ":" + port + "\n";
    }

    public void sslLoop() throws Exception {
        boolean dataDone = false;

        this.createSSLEngines();
        this.createBuffers();

        SSLEngineResult clientResult, serverResult;
        while (!isEngineClosed(this.clientEngine) || !isEngineClosed(this.serverEngine)) {
            clientResult = this.clientEngine.wrap(clientOut, cTOs);
            System.out.println("client wrap: " + clientResult);
            runDelegatedTasks(clientResult, this.clientEngine);

            serverResult = serverEngine.wrap(serverOut, sTOc);
            System.out.println("server wrap: " + serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            this.cTOs.flip();
            this.sTOc.flip();

            clientResult = this.clientEngine.unwrap(this.sTOc, clientIn);
            System.out.println("client unwrap: " + clientResult);
            runDelegatedTasks(clientResult, this.clientEngine);

            serverResult = this.serverEngine.unwrap(this.cTOs, serverIn);
            System.out.println("server unwrap: " + serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            this.cTOs.compact();
            this.sTOc.compact();

            /*
             * After we've transfered all application data between the client
             * and server, we close the clientEngine's outbound stream.
             * This generates a close_notify handshake message, which the
             * server engine receives and responds by closing itself.
             *
             * In normal operation, each SSLEngine should call
             * closeOutbound().  To protect against truncation attacks,
             * SSLEngine.closeInbound() should be called whenever it has
             * determined that no more input data will ever be
             * available (say a closed input stream).
             */
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                /*
                 * A sanity check to ensure we got what was sent.
                 */
                clientIn.flip();
                System.out.println(new String(clientIn.array()));
                serverIn.flip();
                System.out.println(new String(serverIn.array()));

                System.out.println("\tClosing clientEngine's *OUTBOUND*...");
                clientEngine.closeOutbound();
                serverEngine.closeOutbound();
                dataDone = true;
            }
        }
    }

    /*
     * If the result indicates that we have outstanding tasks to do,
     * go ahead and run them in this thread.
     */
    private static void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) throws Exception {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                System.out.println("\trunning delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional tasks");
            }
            System.out.println("\tnew HandshakeStatus: " + hsStatus);
        }
    }

    private void createSSLEngines() {
        // server side config
        this.serverEngine = this.sslc.createSSLEngine();
        this.serverEngine.setUseClientMode(false);
        this.serverEngine.setNeedClientAuth(true);

        // client side config
        this.clientEngine = this.sslc.createSSLEngine("client", 80);
        this.clientEngine.setUseClientMode(true);
    }

    private void createBuffers() {
        // assuming the buffer sizes are the same between client and server
        SSLSession session = this.clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        this.clientIn = ByteBuffer.allocate(appBufferMax + 50);
        this.serverIn = ByteBuffer.allocate(appBufferMax + 50);

        this.cTOs = ByteBuffer.allocate(netBufferMax);
        this.sTOc = ByteBuffer.allocate(netBufferMax);

        this.clientOut = ByteBuffer.wrap("Ola! Eu sou o cliente.".getBytes());
        this.serverOut = ByteBuffer.wrap("Ola cliente! Eu sou o server".getBytes());
    }

    private boolean isEngineClosed(SSLEngine engine) {
        return engine.isOutboundDone() && engine.isInboundDone();
    }
}