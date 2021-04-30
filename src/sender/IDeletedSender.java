package sender;

import message.IDeletedMsg;
import message.Message;

import java.util.Random;

public class IDeletedSender extends MessageSender<IDeletedMsg> {
    private final static int MAX_TIMEOUT = 400;

    public IDeletedSender(SockThread sockThread, IDeletedMsg message, MessageHandler handler) {
        super(sockThread, message, handler, false);
    }

    @Override
    public void notify(Message notification) {
        // skip
    }

    @Override
    public void run() {
        Random random = new Random();
        int timeout = random.nextInt(MAX_TIMEOUT + 1);
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        IDeletedSender.super.send();
                    }
                },
                timeout
        );
    }
}
