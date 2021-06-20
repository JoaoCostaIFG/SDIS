package message;

public class IDeletedMsg extends Message {
    public static final String type = "IDELETED";
    private final String fileId;

    public IDeletedMsg(String version, String id, String fileId) {
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
