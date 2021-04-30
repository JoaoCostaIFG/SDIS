package sender;

import message.ChunkMsg;
import message.GetChunkMsg;
import message.Message;
import utils.Pair;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class GetChunkTCPSender extends MessageSender<GetChunkMsg> {
    private static final int MAX_RETRANSMIT = 5;
    private static final long COLLECTION_INTERVAL = 1000; // in ms
    private final AtomicBoolean gotChunk;
    private ChunkMsg notificationMsg;
    private byte[] response;

    public GetChunkTCPSender(SockThread sockThread, GetChunkMsg message, MessageHandler handler) {
        super(sockThread, message, handler);
        this.gotChunk = new AtomicBoolean(false);
    }

    public byte[] getResponse() {
        return response;
    }

    private boolean refersToSameChunk(Message message) {
        if (message.getType().equals(ChunkMsg.type) &&
                message.getFileId().equals(this.message.getFileId())) {
            return ((ChunkMsg) message).getChunkNo() == this.message.getChunkNo();
        }
        return false;
    }

    private boolean handleTcpVersion(ChunkMsg chunkMsg) {
        Socket socket;
        try {
            Pair<String, Integer> tcpInfo = chunkMsg.getTCP();
            socket = new Socket(InetAddress.getByName(tcpInfo.p1), tcpInfo.p2);
        } catch (IOException e) {
            System.err.println("Failed to open TCP socket (GetChunkTCP)");
            return false;
        }

        boolean isGood = true;
        try {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            this.response = inputStream.readAllBytes();
        } catch (IOException e) {
            System.err.println("Failed to read from TCP socket (GetChunkTCP)");
            isGood = false;
        }
        try {
            socket.close();
        } catch (IOException ioException) {
            System.err.println("Failed to close from TCP socket (GetChunkTCP)");
            isGood = false;
        }

        return isGood;
    }

    private boolean handleNormalVersion(ChunkMsg chunkMsg) {
        this.response = chunkMsg.getChunk();
        return true;
    }

    @Override
    public void notify(Message message) {
        if (refersToSameChunk(message)) {
            ChunkMsg chunkMsg = (ChunkMsg) message;
            boolean success;
            if (message.getVersion().equals("2.0")) {
                success = handleTcpVersion(chunkMsg);
            } else {
                success = handleNormalVersion(chunkMsg);
            }

            if (success) {
                this.notificationMsg = chunkMsg;
                this.gotChunk.set(true);
                this.xau();
            }
        }
    }

    @Override
    public void run() {
        for (int i = 0; i < MAX_RETRANSMIT; ++i) {
            super.send();

            try {
                Thread.sleep((long) (COLLECTION_INTERVAL * Math.pow(2, i)));
            } catch (InterruptedException e) {
                this.xau();
                return;
            }

            if (this.gotChunk.get()) {
                this.success.set(true);
                return;
            }
        }
    }
}
