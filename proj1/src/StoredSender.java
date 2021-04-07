import message.Message;
import message.StoredMsg;

import java.util.Random;

public class StoredSender extends MessageSender<StoredMsg> {
    private final static int MAX_TIMEOUT = 400;

    public StoredSender(SockThread sockThread, StoredMsg message, MessageHandler handler) {
        super(sockThread, message, handler);
        this.xau();
    }

    @Override
    public void notify(Message notification) {
    } // Do nothing

    @Override
    public void run() {
        Random random = new Random();
        int timeout = random.nextInt(MAX_TIMEOUT + 1);
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        StoredSender.super.send();
                    }
                },
                timeout
        );
    }
}
