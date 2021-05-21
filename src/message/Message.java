package message;

import chord.ChordInterface;
import chord.ChordNode;

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
    // IMP if destId is null it means that no hops are necessary and the destination is already known
    private Integer destId;
    private List<Integer> path;

    // TODO cleanup this
    public Message(String fileId) {
        this.path = new ArrayList<>();
    }

    public Message(String fileId, InetAddress sourceAddress, int sourcePort, Integer destId) {
        this(fileId);
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

    public String getFileId() {
        return fileId;
    }

    public String getSenderId() {
        return this.id;
    }

    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
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

    public void setSource(ChordNode node) {
        this.sourceAddress = node.getAddress();
        this.sourcePort = node.getPort();
    }

    public void setDestId(Integer destId) {
        this.destId = destId;
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
