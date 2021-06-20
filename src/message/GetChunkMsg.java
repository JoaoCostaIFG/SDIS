package message;

import file.DigestFile;

import java.net.InetAddress;

public class GetChunkMsg extends Message {
    public static final String type = "GETCHUNK";
    private final Integer chunkNo;
    private int responsible;
    private boolean looped;

    public GetChunkMsg(String fileId, int chunkNo, InetAddress sourceDest, int sourcePort, Integer destId)  {
        super(fileId, sourceDest, sourcePort, destId);
        this.chunkNo = chunkNo;
        this.responsible = -1;
        this.looped = false;
    }

    public int getResponsible() {
        return responsible;
    }

    public void setResponsible(int responsible) {
        this.responsible = responsible;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    public void setLooped() {
        this.looped = true;
    }

    public boolean hasLooped() {
        return this.looped;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString()  + " ChunkNo:" + chunkNo + (Message.DEBUG_MODE ? " FileId: " + fileId +
                " ChunkId: " + DigestFile.getId(fileId, chunkNo) : "");
    }
}
