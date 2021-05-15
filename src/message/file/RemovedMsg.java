package message.file;

public class RemovedMsg extends FileMessage {
    public static final String type = "REMOVED";
    private final Integer chunkNo;

    public RemovedMsg(String version, String id, String fileId, int chunkNo) {
        super(version, id, fileId);
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                chunkNo + " " +
                FileMessage.CRLF + FileMessage.CRLF;
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
        return type + " " + this.fileId + " from " + super.id;
    }
}
