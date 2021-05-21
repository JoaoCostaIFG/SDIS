package message;

public class DeleteMsg extends Message {
    public static final String type = "DELETE";
    private final String fileId;

    public DeleteMsg(String fileId) {
        super(fileId);
        this.fileId = fileId;
    }

    public String getFileId() {
        return fileId;
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
