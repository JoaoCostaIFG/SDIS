package sender;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

public class SSLEngineData {
    public SSLEngine engine;
    public ByteBuffer myAppData, myNetData, peerAppData, peerNetData;

    public SSLEngineData(SSLEngine engine, ByteBuffer myAppData, ByteBuffer myNetData, ByteBuffer peerAppData, ByteBuffer peerNetData) {
        this.engine = engine;
        this.myAppData = myAppData;
        this.myNetData = myNetData;
        this.peerAppData = peerAppData;
        this.peerNetData = peerNetData;
    }
}
