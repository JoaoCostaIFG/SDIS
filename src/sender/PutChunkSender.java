package sender;

import message.Message;
import message.PutChunkMsg;
import state.State;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PutChunkSender extends MessageSender<PutChunkMsg> {
    private static final int MAX_RETRANSMIT = 5;
    private static final long COLLECTION_INTERVAL = 1000; // in ms
    // used to reschedule himself
    private final ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor();
    private int i;

    public PutChunkSender(SockThread sockThread, PutChunkMsg msg, MessageHandler handler) {
        super(sockThread, msg, handler, false);
        this.i = 0;
    }

    public void restart() {
        super.send();
        this.threadPool.schedule(this,
                (long) (COLLECTION_INTERVAL * Math.pow(2, this.i)), TimeUnit.MILLISECONDS);
    }

    @Override
    public void notify(Message message) {
        // skip
    }

    @Override
    public void run() {
        if (State.st.isChunkOk(this.message.getFileId(), this.message.getChunkNo())) {
            this.success.set(true);
            return;
        }

        ++this.i;
        if (this.i == MAX_RETRANSMIT) {
            return;
        }

        this.restart();
    }
}
