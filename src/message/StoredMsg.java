package message;

public class StoredMsg extends Message {
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
                Message.CRLF + Message.CRLF;

        this.fileId = fileId;
        this.chunkNo = chunkNo;
    }

    public Integer getChunkNo() {
        return chunkNo;
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
