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
    private final ServerSocketChannel serverSocketChannel;
    private final InetAddress address;
    private final Integer port;
    private final Observer observer;

    private SSLContext sslc;

    public SockThread(InetAddress address, Integer port, Observer chordNode) throws IOException {
        this.address = address;
        this.port = port;
        this.observer = chordNode;

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
        this.serverSocketChannel.bind(new InetSocketAddress(address, port), MAX_CONNS);
    }

    public InetAddress getAddress() {
        return address;
    }

    public Integer getPort() {
        return port;
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

    private ByteBuffer enlargeBuffer(ByteBuffer oldBuf, int proposedSize) {
        return ByteBuffer.allocate(proposedSize <= oldBuf.limit() ?
                proposedSize * 2 : proposedSize);
    }

    private ByteBuffer enlargeNetBuffer(SSLEngine engine, ByteBuffer oldBuf) {
        return this.enlargeBuffer(oldBuf, engine.getSession().getPacketBufferSize());
    }

    private ByteBuffer enlargeAppBuffer(SSLEngine engine, ByteBuffer oldBuf) {
        return this.enlargeBuffer(oldBuf, engine.getSession().getApplicationBufferSize());
    }

    private ByteBuffer handleNetUnderflow(SSLEngine engine, ByteBuffer buf) {
        ByteBuffer newBuf = this.enlargeNetBuffer(engine, buf);
        buf.flip();
        newBuf.put(buf);
        return newBuf;
    }

    private void runDelegatedTasks(SSLEngine engine) {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            this.threadPool.execute(task);
        }
    }

    private int doHandshake(SSLEngine engine, SocketChannel socketChannel,
                            ByteBuffer myNetData, ByteBuffer peerNetData) throws IOException {
        // See example 8-2 of the docs
        // https://docs.oracle.com/en/java/javase/11/security/java-secure-socket-extension-jsse-reference-guide.html#GUID-AC6700ED-ADC4-41EA-B111-2AEF2CBF7744

        // Create byte buffers to use for holding application data
        int appBufferSize = engine.getSession().getApplicationBufferSize() + 50;
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);

        engine.beginHandshake();

        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED &&
                hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            SSLEngineResult res;
            switch (hs) {
                case NEED_UNWRAP -> {
                    if (socketChannel.read(peerNetData) < 0) {
                        // end of stream => no more io
                        engine.closeInbound();
                        engine.closeOutbound();
                        hs = engine.getHandshakeStatus();
                        break;
                    }
                    // process incoming handshake data
                    peerNetData.flip();
                    res = engine.unwrap(peerNetData, peerAppData);
                    peerNetData.compact();
                    hs = res.getHandshakeStatus();
                    // check status
                    switch (res.getStatus()) {
                        case OK -> {
                            // do nothing ?
                        }
                        case BUFFER_UNDERFLOW -> {
                            // bad packet, or the client maximum fragment size config does not work?
                            // we can increase the size
                            peerNetData = this.handleNetUnderflow(engine, peerNetData);
                        }
                        case BUFFER_OVERFLOW -> {
                            // the client maximum fragment size config does not work?
                            peerAppData = this.enlargeAppBuffer(engine, peerAppData);
                        }
                        case CLOSED -> {
                            if (engine.isOutboundDone()) {
                                // the engine was closed but we still have handshake data to send => failed handshake
                                return 1;
                            } else {
                                // I guess we're done too
                                engine.closeOutbound();
                                hs = engine.getHandshakeStatus(); // collect new status
                            }
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + res.getStatus());
                    }
                }
                case NEED_WRAP -> {
                    // ensure that any previous net data in myNetData has been sent to the peer
                    /*
                    while (myNetData.hasRemaining())
                        socketChannel.write(myNetData);
                     */
                    // clear the rest of the buffer
                    myNetData.clear();

                    // generate handshake data to send (if possible)
                    res = engine.wrap(myAppData, myNetData);
                    hs = res.getHandshakeStatus();
                    switch (res.getStatus()) {
                        case OK -> {
                            myNetData.flip();
                            while (myNetData.hasRemaining())
                                socketChannel.write(myNetData);
                        }
                        case BUFFER_OVERFLOW -> {
                            // the client maximum fragment size config does not work?
                            // we can increase the size
                            myNetData = this.enlargeNetBuffer(engine, myNetData);
                        }
                        case BUFFER_UNDERFLOW -> {
                            throw new SSLException("Buffer underflow on wrap.");
                        }
                        case CLOSED -> {
                            myNetData.flip();
                            while (myNetData.hasRemaining())
                                socketChannel.write(myNetData);
                            peerNetData.clear();
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + res.getStatus());
                    }
                }
                case NEED_TASK -> {
                    runDelegatedTasks(engine);
                    hs = engine.getHandshakeStatus();
                }
                default -> throw new IllegalStateException("Unexpected value: " + hs);
            }
        }

        return 0;
    }

    private void closeSSLConnection(SSLEngine engine, SocketChannel socketChannel) {
        if (engine.isOutboundDone() && engine.isInboundDone()) // if engine is closed => go away
            return;

        engine.closeOutbound();

        int netBufferMax = engine.getSession().getPacketBufferSize();
        ByteBuffer myNetData = ByteBuffer.allocate(netBufferMax);
        ByteBuffer peerNetData = ByteBuffer.allocate(netBufferMax);
        try {
            this.doHandshake(engine, socketChannel, myNetData, peerNetData);
        } catch (IOException ignored) {
        }

        try {
            socketChannel.close(); // :(
        } catch (IOException ignored) {
        }
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
                if (this.doHandshake(engine, socketChannel, bufs[1], bufs[3]) != 0) {
                    System.err.println("Handshake failed");
                    continue; // go to next request
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            // receive loop - read TLS encoded data from peer
            int n = 0;
            while (n == 0) {
                // == 0 -> no bytes read. Try again.
                try {
                    n = socketChannel.read(bufs[3]);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            if (n == -1) {
                // end-of-stream
                System.err.println("Got end of stream from peer. Attempting to close connection.");
                this.closeSSLConnection(engine, socketChannel);
                continue;
            } else if (n > 0) {
                // process incoming data
                bufs[3].flip();
                while (bufs[3].hasRemaining()) {
                    SSLEngineResult res;
                    try {
                        res = engine.unwrap(bufs[3], bufs[2]);
                    } catch (SSLException e) {
                        e.printStackTrace();
                        break;
                    }
                    switch (res.getStatus()) {
                        case OK -> {
                            bufs[2].flip();
                            // flip it to create the message object bellow
                        }
                        case BUFFER_UNDERFLOW -> {
                            bufs[3] = this.handleNetUnderflow(engine, bufs[3]);
                        }
                        case BUFFER_OVERFLOW -> {
                            bufs[2] = this.enlargeAppBuffer(engine, bufs[2]);
                        }
                        case CLOSED -> {
                            this.closeSSLConnection(engine, socketChannel);
                        }
                    }
                }
            }

            this.closeSSLConnection(engine, socketChannel);
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
            // handle message
            this.threadPool.execute(
                    () -> {
                        this.observer.notify(msg);
                    });
        }
    }

    public void send(Message message) {
        System.out.println("Sent: " + message + "\n");

        InetAddress address = message.getDestAddress();
        int port = message.getDestPort();
        // create socket channel
        SocketChannel socketChannel;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(address, port));
            while (!socketChannel.finishConnect()) {
                // busy-wait
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // create SSLEngine
        SSLEngine engine = this.sslc.createSSLEngine(address.getHostAddress(), port);
        engine.setUseClientMode(true);
        // create buffers
        ByteBuffer[] bufs = this.createBuffers(engine);
        try {
            if (this.doHandshake(engine, socketChannel, bufs[1], bufs[3]) != 0) {
                System.err.println("Handshake failed");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // data to send
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream outputStream = new ObjectOutputStream(bos);
            outputStream.writeObject(message);
            outputStream.flush();
            bufs[0].put(bos.toByteArray());
            bufs[0].flip();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // send loop
        while (bufs[0].hasRemaining()) {
            SSLEngineResult res;
            try {
                res = engine.wrap(bufs[0], bufs[1]);
            } catch (SSLException e) {
                e.printStackTrace();
                return;
            }
            switch (res.getStatus()) {
                case OK -> {
                    bufs[1].flip();
                    // send TLS encoded data to peer
                    while (bufs[1].hasRemaining()) {
                        try {
                            socketChannel.write(bufs[1]);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }
                case BUFFER_OVERFLOW -> {
                    bufs[1] = this.enlargeNetBuffer(engine, bufs[1]);
                }
                case BUFFER_UNDERFLOW -> {
                    System.err.println("Buffer underflow that I don't understand.");
                    return;
                }
                case CLOSED -> {
                    this.closeSSLConnection(engine, socketChannel);
                }
            }
        }

        this.closeSSLConnection(engine, socketChannel);
    }

    @Override
    public String toString() {
        return address + ":" + port + "\n";
    }
}