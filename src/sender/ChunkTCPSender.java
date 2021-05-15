package sender;

import file.DigestFile;
import message.Message;
import message.file.ChunkMsg;
import message.file.FileMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkTCPSender extends MessageSender<ChunkMsg> {
    private final static int MAX_DELAY_TIMEOUT = 400;
    private final static int MAX_TIMEOUT_TCP = 10000;
    private final AtomicBoolean chunkAlreadySent;
    private byte[] chunk;

    public ChunkTCPSender(SockThread sockThread, ChunkMsg message, MessageHandler handler) {
        super(sockThread, message, handler);
        this.chunkAlreadySent = new AtomicBoolean(false);
        try {
            this.chunk = DigestFile.readChunk(message.getFileId(), message.getChunkNo());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean refersToSameChunk(Message message) {
        if (message.getType().equals(ChunkMsg.type) &&
                ((ChunkMsg) message).getFileId().equals(this.message.getFileId())) {
            return ((ChunkMsg) message).getChunkNo() == this.message.getChunkNo();
        }
        return false;
    }

    @Override
    public void notify(Message message) {
        if (refersToSameChunk(message)) {
            this.chunkAlreadySent.set(true);
            this.xau();
        }
    }

    @Override
    public void run() {
        Random random = new Random();
        int timeout = random.nextInt(MAX_DELAY_TIMEOUT + 1);
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        // Check if a connection was already opened
                        if (ChunkTCPSender.this.chunkAlreadySent.get())
                            return;
                        // Create Socket
                        ServerSocket serverSocket;
                        Socket socket;
                        try {
                            serverSocket = new ServerSocket(0);
                            serverSocket.setSoTimeout(MAX_TIMEOUT_TCP);
                        } catch (IOException e) {
                            System.err.println("Could not create socket to listen to (ChunkTCP)");
                            return;
                        }
                        // If a connection was already created while we opened socket close it and go bye
                        if (ChunkTCPSender.this.chunkAlreadySent.get()) {
                            try {
                                serverSocket.close();
                            } catch (IOException e) {
                                System.err.println("Failed to close socket (ChunkTCP)");
                            }
                            return;
                        }
                        // Send ChunkTCP Message with the respective ip and port
                        String ip = serverSocket.getInetAddress().getHostAddress();
                        int port = serverSocket.getLocalPort();
                        ChunkTCPSender.super.message.setTCPAddr(ip, port);
                        ChunkTCPSender.super.send();
                        // Wait for someone to connect
                        try {
                            socket = serverSocket.accept();
                        } catch (IOException e) {
                            // System.err.println("Timed out while waiting for answer (ChunkTCP)");
                            try {
                                serverSocket.close();
                            } catch (IOException ioException) {
                                System.err.println("Failed to close socket (ChunkTCP)");
                            }
                            ChunkTCPSender.this.xau();
                            return;
                        }
                        try {
                            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                            outputStream.write(ChunkTCPSender.this.chunk);
                        } catch (IOException e) {
                            System.err.println("Failed to send chunk message (ChunkTCP)");
                        }
                        ChunkTCPSender.this.xau();
                        try {
                            socket.close();
                            serverSocket.close();
                        } catch (IOException ioException) {
                            System.err.println("Failed to close socket (ChunkTCP)");
                        }
                    }
                },
                timeout
        );
    }
}
