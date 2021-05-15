package message.file;

public class IDeletedMsg extends FileMessage {
    public static final String type = "IDELETED";
    private final String fileId;

    public IDeletedMsg(String version, String id, String fileId) {
        super(version, id, fileId);
        this.fileId = fileId;
        this.header = version + " " +
                type + " " +
                id + " " +
                this.fileId + " " +
                FileMessage.CRLF + FileMessage.CRLF;
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
