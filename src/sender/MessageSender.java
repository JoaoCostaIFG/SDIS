package sender;

import message.Message;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MessageSender<T extends Message> implements Runnable, Observer {
    protected final AtomicBoolean success;
    private final MessageHandler handler;
    protected SockThread sockThread;
    protected T message;

    public MessageSender(SockThread sockThread, T message, MessageHandler handler, boolean wantNotifications) {
        this.sockThread = sockThread;
        this.message = message;
        this.success = new AtomicBoolean(false);
        this.handler = handler;
        if (wantNotifications)
            this.handler.addObserver(this);
    }

    public MessageSender(SockThread sockThread, T message, MessageHandler handler) {
        this(sockThread, message, handler, true);
    }

    public boolean getSuccess() {
        return this.success.get();
    }

    public T getMessage() {
        return this.message;
    }

    protected void xau() {
        this.handler.rmObserver(this);
    }

    protected void send() {
        this.sockThread.send(this.message);
    }
}
