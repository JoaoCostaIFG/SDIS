package sender;

import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SSLEngineData {
    public boolean isServer;
    public SSLEngine engine;
    public ByteBuffer myAppData, myNetData, peerAppData, peerNetData;
    public ByteArrayOutputStream content;
    public ExecutorService thread;

    public SSLEngineData(SSLEngine engine, ByteBuffer myAppData, ByteBuffer myNetData,
                         ByteBuffer peerAppData, ByteBuffer peerNetData, boolean isServer) {
        this.engine = engine;
        this.myAppData = myAppData;
        this.myNetData = myNetData;
        this.peerAppData = peerAppData;
        this.peerNetData = peerNetData;
        this.content = new ByteArrayOutputStream();
        this.isServer = isServer;

        if (isServer) this.thread = Executors.newSingleThreadExecutor();
        else this.thread = null;
    }
}
