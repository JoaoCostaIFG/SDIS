package sender;

import message.Message;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MessageSender<T extends Message> implements Runnable, Observer {
    protected final AtomicBoolean success;
    private final MessageHandler handler;
    private final int port;
    private final InetAddress address;
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

    public MessageSender(SockThread sockThread, T message, MessageHandler handler, InetAddress address, int port) {
        this(sockThread, message, handler, address, port,true);
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
