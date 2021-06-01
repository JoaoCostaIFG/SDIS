package message;

import java.net.InetAddress;

public class RemovedMsg extends Message {
    public static final String type = "REMOVED";
    private final Integer chunkNo;
    private final boolean toPredecessor;
    private final int chunkId;

    public RemovedMsg(String fileId, int chunkNo, InetAddress sourceDest, int sourcePort, int destId, int chunkId, boolean toPredecessor) {
        super(fileId, sourceDest, sourcePort, destId);
        this.chunkNo = chunkNo;
        this.chunkId = chunkId;
        this.toPredecessor = toPredecessor;
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

    public boolean isToPredecessor() {
        return toPredecessor;
    }

    @Override
    public String toString() {
        return super.toString() + (Message.DEBUG_MODE ? " FileId: " + fileId : "") + " ChunkId: " + chunkId;
    }
}
