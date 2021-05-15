package message.file;

import message.Message;

public abstract class FileMessage extends Message {
    public static final String type = "FILEMESSAGE";
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

    public FileMessage(String version, String id, String fileId) {
        super(version, id);
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                FileMessage.CRLF + FileMessage.CRLF;
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

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
