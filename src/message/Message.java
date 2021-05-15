package message;

public abstract class Message {
    public static final String type = "CHUNK";
    public static String CRLF = String.valueOf((char) 0xD) + ((char) 0xA);
    public static int versionField = 0;
    public static int typeField = 1;
    public static int idField = 2;
    public static int fileField = 3;
    public static int chunkField = 4;
    public static int replicationField = 5;

    protected String header;
    protected String version;
    protected String id;
    protected String fileId;

    public Message(String version, String id, String fileId) {
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                Message.CRLF + Message.CRLF;
        this.version = version;
        this.id = id;
        this.fileId = fileId;
    }

    public Message(String id, String fileId) {
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                Message.CRLF + Message.CRLF;
        this.version = version;
        this.id = id;
        this.fileId = fileId;
    }

    public String getVersion() {
        return version;
    }

    public abstract String getSockName();

    public abstract String getType();

    public abstract int getHeaderLen();

    public String getFileId() {
        return fileId;
    }

    public byte[] getContent() {
        return header.getBytes();
    }

    public String getSenderId() {
        return this.id;
    }
}
