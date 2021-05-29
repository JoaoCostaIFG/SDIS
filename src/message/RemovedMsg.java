package message;

import java.net.InetAddress;

public class RemovedMsg extends Message {
    public static final String type = "REMOVED";
    private final Integer chunkNo;

    public RemovedMsg(String fileId, int chunkNo, InetAddress sourceDest, int sourcePort, Integer destId) {
        super(fileId, sourceDest, sourcePort, destId);
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
        return super.toString() + " FileId:" + fileId + " ChunkNo:" + chunkNo;
    }
}
