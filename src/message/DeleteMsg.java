package message;

public class DeleteMsg extends Message {
    public static final String type = "DELETE";
    private final String fileId;

    public DeleteMsg(String version, String id, String fileId) {
        super(version, id, fileId);
        this.fileId = fileId;
        this.header = version + " " +
                type + " " +
                id + " " +
                this.fileId + " " +
                Message.CRLF + Message.CRLF;
    }

    @Override
    public String getSockName() {
        return "MC";
    }

    public String getFileId() {
        return fileId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getHeaderLen() {
        return 4;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " from " + super.id;
    }
}
