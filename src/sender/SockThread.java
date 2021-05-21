package sender;

import file.DigestFile;
import message.Message;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SockThread implements Runnable {
    private static final int MAX_CONNS = 5;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final String name;
    private final ServerSocketChannel serverSocketChannel;
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
            // password for the keys
            char[] passphrase = "123456".toCharArray();
            // initialize key and trust material
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("../keys/client.keys"), passphrase);
            KeyStore ts = KeyStore.getInstance("JKS");
            ts.load(new FileInputStream("../keys/truststore"), passphrase);

            // KeyManager decides which key to use
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);
            // TrustManager decides whether to allow connections
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ts);

            // get instance of SSLContext for TLS protocols
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            this.sslc = sslCtx;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | UnrecoverableKeyException | KeyManagementException e) {
            e.printStackTrace();
        }

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress(ip, port), MAX_CONNS);
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
            this.serverSocketChannel.close();
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
            SocketChannel socketChannel;
            try {
                socketChannel = this.serverSocketChannel.accept();
            } catch (IOException e) {
                System.err.println("Timed out while waiting for answer (Sock thread) " + this);
                continue;
            }

            ByteBuffer inBuff = ByteBuffer.allocate(DigestFile.MAX_CHUNK_SIZE + 1000);
            while (true) {
                try {
                    if (!(inBuff.hasRemaining() && socketChannel.read(inBuff) != -1)) break;
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(inBuff.array());
            Message msg;
            try {
                ObjectInput in = new ObjectInputStream(bis);
                msg = (Message) in.readObject();
                bis.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            this.threadPool.execute(
                    () -> {
                        assert msg != null;
                        handler.handleMessage(msg);
                    });
        }
    }

    public void send(Message message) {
        System.out.println("Sent: " + message);
        try {
            InetAddress address = message.getDestAddress();
            int port = message.getDestPort();
            // create socket channel
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(address, port));

            // TODO busy-wait
            while (!socketChannel.finishConnect()) {
                System.out.println("Connecting...");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ObjectOutputStream out = new ObjectOutputStream(bos);
                out.writeObject(message);
                out.flush();
                ByteBuffer clientOut = ByteBuffer.wrap(bos.toByteArray());
                socketChannel.write(clientOut);
            }

            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return ip + ":" + port + "\n";
    }

    // public void sslLoop() throws Exception {
    //     boolean dataDone = false;

    //     this.createSSLEngines();
    //     this.createBuffers();

    //     SSLEngineResult clientResult, serverResult;
    //     while (!isEngineClosed(this.clientEngine) || !isEngineClosed(this.serverEngine)) {
    //         /* SEND CLIENT DATA */
    //         clientResult = this.clientEngine.wrap(clientOut, cTOs);
    //         System.out.println("client wrap: " + clientResult);
    //         runDelegatedTasks(clientResult, this.clientEngine);

    //         this.socketChannel.write(cTOs);

    //         /* READ CLIENT DATA */
    //         this.socketChannel.read(cTOs);
    //         this.cTOs.flip();

    //         serverResult = this.serverEngine.unwrap(cTOs, serverIn);
    //         System.out.println("server unwrap: " + serverResult);
    //         runDelegatedTasks(serverResult, serverEngine);

    //         /* SEND SERVER DATA */
    //         serverResult = this.serverEngine.wrap(serverOut, sTOc);
    //         System.out.println("server wrap: " + serverResult);
    //         runDelegatedTasks(serverResult, serverEngine);

    //         this.socketChannel.write(sTOc);

    //         /* READ SERVER DATA */
    //         this.socketChannel.read(sTOc);
    //         this.sTOc.flip();

    //         clientResult = this.clientEngine.unwrap(sTOc, clientIn);
    //         System.out.println("client unwrap: " + clientResult);
    //         runDelegatedTasks(clientResult, this.clientEngine);


    //         this.cTOs.compact();
    //         this.sTOc.compact();

    //         if (!dataDone && (clientOut.limit() == serverIn.position()) &&
    //                 (serverOut.limit() == clientIn.position())) {

    //             /*
    //              * A sanity check to ensure we got what was sent.
    //              */
    //             clientIn.flip();
    //             System.out.println(new String(clientIn.array()));
    //             serverIn.flip();
    //             System.out.println(new String(serverIn.array()));

    //             System.out.println("\tClosing clientEngine's *OUTBOUND*...");
    //             clientEngine.closeOutbound();
    //             serverEngine.closeOutbound();
    //             dataDone = true;
    //         }
    //     }
    // }

    // /*
    //  * If the result indicates that we have outstanding tasks to do,
    //  * go ahead and run them in this thread.
    //  */
    // private static void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) throws Exception {
    //     if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
    //         Runnable runnable;
    //         while ((runnable = engine.getDelegatedTask()) != null) {
    //             System.out.println("\trunning delegated task...");
    //             runnable.run();
    //         }
    //         SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
    //         if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
    //             throw new Exception("handshake shouldn't need additional tasks");
    //         }
    //         System.out.println("\tnew HandshakeStatus: " + hsStatus);
    //     }
    // }

    // private void createSSLEngines() {
    //     // server side config
    //     this.serverEngine = this.sslc.createSSLEngine();
    //     this.serverEngine.setUseClientMode(false);
    //     this.serverEngine.setNeedClientAuth(true);

    //     // client side config
    //     this.clientEngine = this.sslc.createSSLEngine("localhost", 8001);
    //     this.clientEngine.setUseClientMode(true);
    // }

    // private void createBuffers() {
    //     // assuming the buffer sizes are the same between client and server
    //     SSLSession session = this.clientEngine.getSession();
    //     int appBufferMax = session.getApplicationBufferSize();
    //     int netBufferMax = session.getPacketBufferSize();

    //     this.clientIn = ByteBuffer.allocate(appBufferMax + 50);
    //     this.serverIn = ByteBuffer.allocate(appBufferMax + 50);

    //     this.cTOs = ByteBuffer.allocate(netBufferMax);
    //     this.sTOc = ByteBuffer.allocate(netBufferMax);

    //     this.clientOut = ByteBuffer.wrap("Ola! Eu sou o cliente.".getBytes());
    //     this.serverOut = ByteBuffer.wrap("Ola cliente! Eu sou o server".getBytes());
    // }

    // private boolean isEngineClosed(SSLEngine engine) {
    //     return engine.isOutboundDone() && engine.isInboundDone();
    // }
}