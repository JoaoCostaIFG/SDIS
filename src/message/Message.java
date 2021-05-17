package message;

import chord.ChordInterface;

import java.io.Serializable;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public abstract class Message implements Serializable {
    public static final String type = "FILEMESSAGE";
    public static final String CRLF = "END";

    protected String header;
    protected String version;
    protected String id;
    protected String fileId;
    private InetAddress destAddress;
    private int destPort;
    private InetAddress sourceAddress;
    private int sourcePort;
    private Integer destId;
    private List<Integer> path;

    // TODO cleanup this
    public Message(String version, String id, String fileId) {
        this.header = version + " " +
                type + " " +
                id + " " +
                fileId + " " +
                Message.CRLF + Message.CRLF;
        this.version = version;
        this.id = id;
        this.path = new ArrayList<>();
    }

    public Message(String version, String id, String fileId, InetAddress sourceAddress, int sourcePort, Integer destId) {
        this(version, id, fileId);
        this.destId = destId;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
    }

    public void addToPath(Integer id) {
        this.path.add(id);
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

    public InetAddress getDestAddress() {
        return this.destAddress;
    }

    public int getDestPort() {
        return destPort;
    }

    public Integer getDestId() {
        return destId;
    }

    public void setDest(InetAddress address, int port) {
        this.destAddress = address;
        this.destPort = port;
    }

    public void setDest(ChordInterface nextHopDest) throws RemoteException {
        this.setDest(nextHopDest.getAddress(), nextHopDest.getPort());
    }

    @Override
    public String toString() {
        return this.getType() + "{" +
                "From:" + sourceAddress + ":" + sourcePort + " " +
                "To:" + destAddress + ":" + destPort + " " +
                "destId:" + destId + " " +
                "path" + path +
                "}";
    }
}
