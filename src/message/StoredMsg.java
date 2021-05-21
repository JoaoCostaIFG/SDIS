package message;

import java.net.InetAddress;

public class StoredMsg extends Message {
    public static final String type = "STORED";
    private final String fileId;
    private final int chunkNo;

    public StoredMsg(String fileId, InetAddress sourceDest, int sourcePort, int chunkNo) {
        super(fileId, sourceDest, sourcePort, null); // destId isn't relevant, hops won't be made
        this.fileId = fileId;
        this.chunkNo = chunkNo;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + " FileId: " + fileId;
    }
}
