package message.file;

public class DeleteMsg extends FileMessage {
    public static final String type = "DELETE";
    private final String fileId;

    public DeleteMsg(String version, String id, String fileId) {
        super(version, id, fileId);
        this.fileId = fileId;
        this.header = version + " " +
                type + " " +
                id + " " +
                this.fileId + " " +
                FileMessage.CRLF + FileMessage.CRLF;
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
