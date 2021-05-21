package message;

public class RemovedMsg extends Message {
    public static final String type = "REMOVED";
    private final Integer chunkNo;

    public RemovedMsg(String fileId, int chunkNo) {
        super(fileId);
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
    public String toString() {
        return type + " " + this.fileId + " from " + super.id;
    }
}
