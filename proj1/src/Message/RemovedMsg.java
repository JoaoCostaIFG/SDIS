package Message;

public class RemovedMsg implements Message {
    public static final String type = "REMOVED";
    private final String header;
    private final String fileId;
    private final Integer chunkNo;

    public RemovedMsg(String version, String id, String fileId, int chunkNo) {
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                chunkNo + " " +
                Message.CRLF + Message.CRLF;
        this.fileId = fileId;
        this.chunkNo = chunkNo;
    }

    public String getFileId() {
        return fileId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    @Override
    public byte[] getContent() {
        return header.getBytes();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getHeaderLen() {
        return 5;
    }
}
