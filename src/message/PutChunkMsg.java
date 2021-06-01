package message;

import java.net.InetAddress;

public class PutChunkMsg extends Message {
    public static final String type = "PUTCHUNK";
    private final Integer chunkNo;
    private Integer replication;
    private byte[] chunk;
    private int seqNumber;

    public PutChunkMsg(String fileId,
                       int chunkNo, byte[] chunk, int replication, InetAddress sourceDest, int sourcePort, Integer destId) {
        super(fileId, sourceDest, sourcePort, destId);
        this.chunkNo = chunkNo;
        this.replication = replication;
        this.chunk = chunk;
        this.seqNumber = replication;
    }

    public PutChunkMsg(String fileId, Integer chunkNo, byte[] chunk, int replication, int destId) {
        super(fileId, null, -1, destId); // The source is set later by the responsible node when it receives this message
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replication = replication;
        this.chunk = chunk;
        this.seqNumber = replication;
    }

    public void decreaseCurrentRep() {
        --this.seqNumber;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public String getFileId() {
        return fileId;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    public byte[] getChunk() {
        return this.chunk;
    }

    public int getReplication() {
        return replication;
    }

    public boolean hasNoSource() {
        return this.getSourcePort() == -1 && this.getSourceAddress() == null;
    }

    public void setReplication(int replication) {
        this.replication = replication;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public void setChunk(byte[] chunk) {
        this.chunk = chunk;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + (Message.DEBUG_MODE ? " FileId: " + fileId + " ChunkNo:" + chunkNo : "")
                + " Rep:" + replication + " SeqNum:" + seqNumber;
    }
}
