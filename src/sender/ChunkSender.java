package sender;

import message.ChunkMsg;
import message.Message;

import java.util.Random;

public class ChunkSender extends MessageSender<ChunkMsg> {
    private final static int MAX_TIMEOUT = 400;
    private boolean chunkAlreadySent;

    public ChunkSender(SockThread sockThread, ChunkMsg message, MessageHandler handler) {
        super(sockThread, message, handler);
        this.chunkAlreadySent = false;
    }

    private boolean refersToSameChunk(Message message) {
        if (message.getType().equals(ChunkMsg.type) &&
                ((Message)(message)).getFileId().equals(this.message.getFileId())) {
            return ((ChunkMsg) message).getChunkNo() == this.message.getChunkNo();
        }
        return false;
    }

    @Override
    public void notify(Message message) {
        if (refersToSameChunk(message)) {
            chunkAlreadySent = true;
            this.xau();
        }
    }

    @Override
    public void run() {
        Random random = new Random();
        int timeout = random.nextInt(MAX_TIMEOUT + 1);
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if (!chunkAlreadySent)
                            ChunkSender.super.send();
                    }
                },
                timeout
        );
    }
}
