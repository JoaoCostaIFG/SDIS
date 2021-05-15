package sender;

import message.file.FileMessage;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MessageSender<T extends FileMessage> implements Runnable, Observer {
    protected final AtomicBoolean success;
    private final MessageHandler handler;
    private final int port;
    private InetAddress address;
    protected SockThread sockThread;
    protected T message;

    public MessageSender(SockThread sockThread, T message, MessageHandler handler, InetAddress address, int port, boolean wantNotifications) {
        this.sockThread = sockThread;
        this.message = message;
        this.success = new AtomicBoolean(false);
        this.handler = handler;
        this.address = address;
        this.port = port;
        
        if (wantNotifications)
            this.handler.addObserver(this);
    }

    // TODO Delete this when converting senders to chord, here as a placeholder to compile
    public MessageSender(SockThread sockThread, T message, MessageHandler handler, boolean wantsNotifications) {
        this(sockThread, message, handler, null, 0, wantsNotifications);
    }

    // TODO Delete this when converting senders to chord, here as a placeholder to compile
    public MessageSender(SockThread sockThread, T message, MessageHandler handler) {
        this(sockThread, message, handler, null, 0, true);
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
        this.sockThread.send(this.message, this.address, this.port);
    }
}
