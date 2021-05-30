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
import java.util.Arrays;
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

    private ByteBuffer handleOverflow(SSLEngine engine, ByteBuffer dst) {
        // Could attempt to drain the dst buffer of any already obtained
        // data, but we'll just increase it to the size needed.
        int appSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer b = ByteBuffer.allocate(appSize + dst.position());
        dst.flip();
        b.put(dst);
        return b;
    }

    private ByteBuffer handleUnderflow(SSLEngine engine, ByteBuffer src, ByteBuffer dst) {
        int netSize = engine.getSession().getPacketBufferSize();
        // Resize buffer if needed.
        if (netSize > dst.capacity()) {
            ByteBuffer b = ByteBuffer.allocate(netSize);
            src.flip();
            b.put(src);
            return b;
        }
        return src;
    }

    private void runDelegatedTasks(SSLEngine engine) {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            // this.threadPool.execute(task);
            task.run();
        }
    }

    private int doHandshake(SSLEngine engine, SocketChannel socketChannel,
                            ByteBuffer myNetData, ByteBuffer peerNetData, boolean isEnd) throws IOException {
        // See example 8-2 of the docs
        // https://docs.oracle.com/en/java/javase/11/security/java-secure-socket-extension-jsse-reference-guide.html#GUID-AC6700ED-ADC4-41EA-B111-2AEF2CBF7744

        // Create byte buffers to use for holding application data
        int appBufferSize = engine.getSession().getApplicationBufferSize() + 50;
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);

        if (isEnd) engine.closeOutbound();
        else engine.beginHandshake();

        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED &&
                hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            SSLEngineResult res;
            switch (hs) {
                case NEED_UNWRAP:
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
                        case OK:
                            // do nothing ?
                            break;
                        case BUFFER_OVERFLOW:
                            // the client maximum fragment size config does not work?
                            peerAppData = this.handleOverflow(engine, peerAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            // bad packet, or the client maximum fragment size config does not work?
                            // we can increase the size
                            peerNetData = this.handleUnderflow(engine, peerNetData, peerAppData);
                            // Obtain more inbound network data for src,
                            // then retry the operation.
                            break;
                        case CLOSED:
                            engine.closeOutbound();
                            return 0;
                        default:
                            throw new IllegalStateException("Unexpected value: " + res.getStatus());
                    }
                    break;
                case NEED_WRAP:
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
                        case OK:
                            myNetData.flip();
                            while (myNetData.hasRemaining())
                                socketChannel.write(myNetData);
                            break;
                        case BUFFER_OVERFLOW:
                            // the client maximum fragment size config does not work?
                            // we can increase the size
                            myNetData = this.handleOverflow(engine, myNetData);
                            break;
                        case BUFFER_UNDERFLOW:
                            myAppData = this.handleUnderflow(engine, myAppData, myNetData);
                        case CLOSED:
                            engine.closeOutbound();
                            return 0;
                        default:
                            throw new IllegalStateException("Unexpected value: " + res.getStatus());
                    }
                    break;
                case NEED_TASK:
                    runDelegatedTasks(engine);
                    hs = engine.getHandshakeStatus();
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + hs);
            }
        }

        return 0;
    }

    private int doHandshake(SSLEngine engine, SocketChannel socketChannel,
                            ByteBuffer myNetData, ByteBuffer peerNetData) throws IOException {
        return this.doHandshake(engine, socketChannel, myNetData, peerNetData, false);
    }

    private void closeSSLConnection(SSLEngine engine, SocketChannel socketChannel, ByteBuffer myNetData) throws IOException {
        engine.closeOutbound();
        while (myNetData.hasRemaining())
            socketChannel.write(myNetData);

        myNetData.clear();
        SSLEngineResult byebye = engine.wrap(ByteBuffer.allocate(0), myNetData);
        if (byebye.getStatus() != SSLEngineResult.Status.CLOSED)
            throw new IOException("Invalid state for closure.");

        myNetData.flip();
        while (myNetData.hasRemaining()) {
            try {
                socketChannel.write(myNetData);
            } catch (IOException ignored) {
                break;
            }
        }

        while (!engine.isOutboundDone()) {
            // zzz
        }
        socketChannel.close();
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
                // socketChannel.configureBlocking(false);
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
            do {
                try {
                    n = socketChannel.read(bufs[3]);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            } while (n == 0);

            // end of stream state
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            if (n < 0) {
                System.err.println("Got end of stream from peer. Attempting to close connection.");
                try {
                    engine.closeInbound();
                } catch (SSLException e) {
                    System.err.println("Peer didn't follow the correct connection end procedure.");
                }
                try {
                    this.closeSSLConnection(engine, socketChannel, bufs[1]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                continue;
            } else {
                bufs[3].flip();
                // process incoming data
                while (bufs[3].hasRemaining()) {
                    SSLEngineResult res;
                    try {
                        res = engine.unwrap(bufs[3], bufs[2]);
                    } catch (SSLException e) {
                        e.printStackTrace();
                        break;
                    }

                    switch (res.getStatus()) {
                        case OK:
                            // flip it to create the message object bellow
                            bufs[2].flip();
                            try {
                                content.write(Arrays.copyOfRange(bufs[2].array(), 0, bufs[2].limit()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            bufs[2] = this.handleOverflow(engine, bufs[2]);
                            break;
                        case BUFFER_UNDERFLOW:
                            bufs[3] = this.handleUnderflow(engine, bufs[3], bufs[2]);
                            break;
                    }
                }
            }

            try {
                this.closeSSLConnection(engine, socketChannel, bufs[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // System.out.println("Received aqui\n\n\n");

            // create message instance from the received bytes
            ByteArrayInputStream bis = new ByteArrayInputStream(content.toByteArray());
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
                        this.observer.handle(msg);
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
        byte[] dataToSend;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream outputStream = new ObjectOutputStream(bos);
            outputStream.writeObject(message);
            outputStream.flush();
            dataToSend = bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        bufs[0].put(dataToSend);
        bufs[0].flip();

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
                case OK:
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
                    break;
                case BUFFER_OVERFLOW:
                    bufs[1] = this.handleOverflow(engine, bufs[1]);
                    bufs[1].clear();
                    break;
                case BUFFER_UNDERFLOW:
                    bufs[0] = this.handleUnderflow(engine, bufs[0], bufs[1]);
                    return;
            }
        }

        try {
            this.closeSSLConnection(engine, socketChannel, bufs[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // System.out.println("Sent aqui\n\n\n");
    }

    @Override
    public String toString() {
        return address + ":" + port + "\n";
    }
}