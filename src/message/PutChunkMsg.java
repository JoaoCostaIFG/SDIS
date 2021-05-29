package message;

import java.net.InetAddress;

public class PutChunkMsg extends Message {
    public static final String type = "PUTCHUNK";
    private final Integer chunkNo;
    private final Integer replication;
    private final byte[] chunk;
    private int seqNumber;

    public PutChunkMsg(String fileId,
                       int chunkNo, byte[] chunk, int replication, InetAddress sourceDest, int sourcePort, Integer destId) {
        super(fileId, sourceDest, sourcePort, destId);
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replication = replication;
        this.chunk = chunk;
        this.seqNumber = replication;
    }

    public PutChunkMsg(String fileId, Integer chunkNo, byte[] chunk, int replication, int destId) {
        super(fileId, null, -1, destId);
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

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + " FileId:" + fileId + " ChunkNo:" + chunkNo + " Rep:" + replication + " SeqNum:" + seqNumber;
    }
}
