package sender;

import message.Message;
import message.file.FileMessage;
import message.file.PutChunkMsg;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemovedPutchunkSender extends MessageSender<PutChunkMsg> {
    private final static int MAX_TIMEOUT = 400;
    private final PutChunkSender putChunkSender;
    private final AtomicBoolean putchunkAlreadySent;

    public RemovedPutchunkSender(SockThread sockThread, PutChunkMsg message, MessageHandler handler) {
        super(sockThread, message, handler);
        this.putchunkAlreadySent = new AtomicBoolean(false);
        this.putChunkSender = new PutChunkSender(sockThread, message, handler);
    }

    private boolean refersToSamePutchunk(Message message) {
        if (message.getType().equals(PutChunkMsg.type)) {
            PutChunkMsg putChunkMsg = (PutChunkMsg) message;
            return this.message.getChunkNo() == putChunkMsg.getChunkNo() &&
                    this.message.getFileId().equals(putChunkMsg.getFileId());
        }
        return false;
    }

    @Override
    public void notify(Message message) {
        if (refersToSamePutchunk(message)) {
            this.putchunkAlreadySent.set(true);
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
                        if (!putchunkAlreadySent.get()) {
                            putChunkSender.restart();
                        }
                    }
                },
                timeout
        );
    }
}
