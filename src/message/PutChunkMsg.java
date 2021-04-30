package message;

import java.nio.ByteBuffer;

public class PutChunkMsg extends Message {
    public static final String type = "PUTCHUNK";
    private final Integer chunkNo;
    private final Integer replication;
    private final byte[] chunk;

    public PutChunkMsg(String version, String id, String fileId, int chunkNo, int replication, byte[] chunk) {
        super(version, id, fileId);
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                chunkNo + " " +
                replication + " " +
                Message.CRLF + Message.CRLF;
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replication = replication;
        this.chunk = chunk;
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

    @Override
    public byte[] getContent() {
        byte[] packetContent = super.getContent();
        return ByteBuffer.allocate(packetContent.length + this.chunk.length)
                .put(packetContent).put(this.chunk).array();
    }

    @Override
    public String getSockName() {
        return "MDB";
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getHeaderLen() {
        return 6;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " chunkno. " + this.chunkNo + " rep. " + this.replication + " from " + super.id;
    }
}
