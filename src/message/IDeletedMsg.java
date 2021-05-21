package message;

public class IDeletedMsg extends Message {
    public static final String type = "IDELETED";
    private final String fileId;

    public IDeletedMsg(String fileId) {
        super(fileId);
        this.fileId = fileId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " from " + super.id;
    }
}
