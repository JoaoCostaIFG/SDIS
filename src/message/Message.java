package message;

import java.io.Serializable;

public abstract class Message implements Serializable {
    public static final String type = "MSG";
    public static String CRLF = String.valueOf((char) 0xD) + ((char) 0xA);
    public static int versionField = 0;
    public static int typeField = 1;
    public static int idField = 2;

    protected String header;
    protected String version;
    protected String id;

    public Message(String version, String id) {
        this.header = version + " " +
                type + " " +
                id + " " +
                Message.CRLF + Message.CRLF;
        this.version = version;
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public abstract String getType();

    public int getHeaderLen() { return 3; }

    public byte[] getContent() {
        return header.getBytes();
    }

    public String getSenderId() {
        return this.id;
    }
}
