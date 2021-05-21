package sender;

import message.Message;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            System.err.println("\trunning delegated task...");
            task.run();
        }
    }

    private ByteBuffer enlargeBuffer(ByteBuffer oldBuf, int proposedSize) {
        ByteBuffer replaceBuffer = ByteBuffer.allocate(proposedSize <= oldBuf.limit() ?
                proposedSize * 2 : proposedSize);
        oldBuf.flip();
        replaceBuffer.put(oldBuf);
        return replaceBuffer;
    }

    private ByteBuffer enlargeNetBuffer(SSLEngine engine, ByteBuffer oldBuf) {
        int proposedSize = engine.getSession().getPacketBufferSize();
        return this.enlargeBuffer(oldBuf, proposedSize);
    }

    private ByteBuffer enlargeAppBuffer(SSLEngine engine, ByteBuffer oldBuf) {
        int proposedSize = engine.getSession().getApplicationBufferSize();
        return this.enlargeBuffer(oldBuf, proposedSize);
    }

    private int doHandshake(SSLEngine engine, SocketChannel socketChannel,
                            ByteBuffer myNetData, ByteBuffer peerNetData) throws IOException {
        System.err.println("Handshaking...");

        // Create byte buffers to use for holding application data
        int appBufferSize = engine.getSession().getApplicationBufferSize() + 50;
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
        myNetData.clear();
        peerNetData.clear();

        engine.beginHandshake();

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED &&
                hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            System.out.println(hs);
            switch (hs) {
                case NEED_UNWRAP -> {
                    if (socketChannel.read(peerNetData) < 0) {
                        engine.closeInbound();
                        engine.closeOutbound();
                        hs = engine.getHandshakeStatus();
                        break;
                    }
                    peerNetData.flip();
                    result = engine.unwrap(peerNetData, peerAppData);
                    peerNetData.compact();
                    hs = result.getHandshakeStatus();
                    switch (result.getStatus()) {
                        case OK:
                            break;
                        case BUFFER_OVERFLOW:
                            // the client maximum fragment size config does not work?
                            throw new IOException("Buffer overflow: incorrect client maximum fragment size.");
                        case BUFFER_UNDERFLOW:
                            // bad packet, or the client maximum fragment size config does not work?
                            peerNetData = this.enlargeNetBuffer(engine, peerNetData);
                            break;
                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                return 1;
                            } else {
                                engine.closeOutbound();
                                hs = engine.getHandshakeStatus();
                            }
                            break;
                    }
                }
                case NEED_WRAP -> {
                    myNetData.clear();
                    result = engine.wrap(myAppData, myNetData);
                    hs = result.getHandshakeStatus();
                    switch (result.getStatus()) {
                        case OK -> {
                            myNetData.flip();
                            while (myNetData.hasRemaining())
                                socketChannel.write(myNetData);
                        }
                        case BUFFER_OVERFLOW -> {
                            // the client maximum fragment size config does not work?
                            myNetData = this.enlargeAppBuffer(engine, myNetData);
                        }
                        case BUFFER_UNDERFLOW -> {
                            throw new IOException("Buffer underflow on wrap.");
                        }
                        case CLOSED -> {
                            myNetData.flip();
                            while (myNetData.hasRemaining())
                                socketChannel.write(myNetData);
                            peerNetData.clear();
                        }
                    }
                }
                case NEED_TASK -> {
                    runDelegatedTasks(engine);
                    hs = engine.getHandshakeStatus();
                }
                default -> throw new IOException("WTF?");
            }
        }
        return 0;
    }

    private ByteBuffer[] createBuffers(SSLEngine engine) {
        SSLSession session = engine.getSession();
        // allocate extra space to prevent some overflows
        int appBufferMax = session.getApplicationBufferSize() + 50;
        int netBufferMax = session.getPacketBufferSize();

        ByteBuffer[] ret = new ByteBuffer[4];
        // my appData
        ret[0] = ByteBuffer.allocate(appBufferMax);
        // my netData
        ret[1] = ByteBuffer.allocate(netBufferMax);
        // peer appData
        ret[2] = ByteBuffer.allocate(appBufferMax);
        // peer netData
        ret[3] = ByteBuffer.allocate(netBufferMax);
        return ret;
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

            // create SSLEngine
            SSLEngine engine = this.sslc.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(true);
            // create buffers
            ByteBuffer[] bufs = this.createBuffers(engine);
            try {
                this.doHandshake(engine, socketChannel, bufs[1], bufs[3]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // receive loop

            // close
            System.err.println("Done");
            engine.closeOutbound();
            try {
                socketChannel.close();
            } catch (IOException ignored) {
            }

            // create message instance from the received bytes
            ByteArrayInputStream bis = new ByteArrayInputStream(bufs[2].array());
            Message msg;
            try {
                ObjectInput in = new ObjectInputStream(bis);
                msg = (Message) in.readObject();
                bis.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            System.err.println(msg.getDestAddress());
            this.threadPool.execute(
                    () -> {
                        handler.handleMessage(msg);
                    }
            );
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
            while (!socketChannel.finishConnect()) {
                // TODO busy-wait
                System.out.println("Connecting...");
            }

            // create SSLEngine
            System.out.println(address.getHostAddress());
            SSLEngine engine = this.sslc.createSSLEngine(address.getHostAddress(), 443);
            engine.setUseClientMode(true);
            // create buffers
            ByteBuffer[] bufs = this.createBuffers(engine);
            try {
                this.doHandshake(engine, socketChannel, bufs[1], bufs[3]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // data to send
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                ObjectOutputStream outputStream = new ObjectOutputStream(bos);
                outputStream.writeObject(message);
                outputStream.flush();
                bufs[0].put(bos.toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // send loop

            // close
            System.err.println("Done");
            engine.closeOutbound();
            socketChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isEngineClosed(SSLEngine engine) {
        return engine.isOutboundDone() && engine.isInboundDone();
    }

    @Override
    public String toString() {
        return ip + ":" + port + "\n";
    }
}