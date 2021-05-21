package message;

import java.net.InetAddress;

public class GetChunkMsg extends Message {
    public static final String type = "GETCHUNK";
    private final Integer chunkNo;

    public GetChunkMsg(String fileId, int chunkNo, InetAddress sourceDest, int sourcePort, Integer destId)  {
        super(fileId, sourceDest, sourcePort, destId);
        this.chunkNo = chunkNo;
    }

    public String getFileId() {
        return fileId;
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
