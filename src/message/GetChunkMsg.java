package message;

public class GetChunkMsg extends Message {
    public static final String type = "GETCHUNK";
    private final String fileId;
    private final Integer chunkNo;

    public GetChunkMsg(String version, String id, String fileId, int chunkNo) {
        super(version, id, fileId);
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                chunkNo + " " +
                Message.CRLF + Message.CRLF;
        this.fileId = fileId;
        this.chunkNo = chunkNo;
    }

    public String getFileId() {
        return fileId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    @Override
    public byte[] getContent() {
        return header.getBytes();
    }

    @Override
    public String getSockName() {
        return "MC";
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getHeaderLen() {
        return 5;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " chunkno. " + this.chunkNo + " from " + super.id;
    }
}
