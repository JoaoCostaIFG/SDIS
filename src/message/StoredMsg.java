package message;

import java.net.InetAddress;

public class StoredMsg extends Message {
    public static final String type = "STORED";
    private final int chunkNo;
    private final int chunkId;

    public StoredMsg(String fileId, InetAddress sourceDest, int sourcePort, int chunkNo, int chunkId) {
        super(fileId, sourceDest, sourcePort, null); // destId isn't relevant, hops won't be made
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.chunkId= chunkId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    public int getChunkId() {
        return chunkId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + (Message.DEBUG_MODE ? " FileId: " + fileId : "") + " ChunkId: " + chunkId;
    }
}
