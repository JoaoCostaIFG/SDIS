package message.file;

public class StoredMsg extends FileMessage {
    public static final String type = "STORED";
    private final String fileId;
    private final int chunkNo;

    public StoredMsg(String version, String id, String fileId, int chunkNo) {
        super(version, id, fileId);
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                chunkNo + " " +
                FileMessage.CRLF + FileMessage.CRLF;

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
    public int getHeaderLen() {
        return 5;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " chunkno. " + this.chunkNo + " from " + super.id;
    }
}
