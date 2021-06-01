package sender;

import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class SSLEngineData {
    public boolean isServer;
    public SSLEngine engine;
    public ByteBuffer myAppData, myNetData, peerAppData, peerNetData;
    public ByteArrayOutputStream content;

    public SSLEngineData(SSLEngine engine, ByteBuffer myAppData, ByteBuffer myNetData,
                         ByteBuffer peerAppData, ByteBuffer peerNetData, boolean isServer) {
        this.engine = engine;
        this.myAppData = myAppData;
        this.myNetData = myNetData;
        this.peerAppData = peerAppData;
        this.peerNetData = peerNetData;
        this.content = new ByteArrayOutputStream();
        this.isServer = isServer;
    }
}
