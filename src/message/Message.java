package message;

import chord.ChordController;
import chord.ChordInterface;

import java.io.Serializable;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

public abstract class Message implements Serializable {
    public static final boolean DEBUG_MODE = false;

    protected String fileId;
    private InetAddress destAddress;
    private int destPort;
    private InetAddress sourceAddress;
    private int sourcePort;
    // IMP if destId is null it means that no hops are necessary and the destination is already known
    private Integer destId;
    private List<Integer> path;

    public Message(String fileId, InetAddress sourceAddress, int sourcePort, Integer destId) {
        this.fileId = fileId;
        this.path = new ArrayList<>();
        this.destId = destId;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
    }

    public abstract String getType();

    /* GETTERS */

    public String getFileId() {
        return fileId;
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

    public boolean destAddrKnown() {
        return destId == null; // we don't know the destination and we are trying to figure it out
    }

    public boolean hasNoSource() {
        return this.getSourcePort() == -1 && this.getSourceAddress() == null;
    }

    /* SETTERS */
    public void setDest(InetAddress address, int port) {
        this.destAddress = address;
        this.destPort = port;
    }

    public void setDest(ChordInterface nextHopDest) throws RemoteException {
        this.setDest(nextHopDest.getAddress(), nextHopDest.getPort());
    }

    public void setSource(ChordController node) {
        this.sourceAddress = node.getAddress();
        this.sourcePort = node.getPort();
    }

    public void setDestId(Integer destId) {
        this.destId = destId;
    }

    public void addToPath(Integer id) {
        this.path.add(id);
    }

    @Override
    public String toString() {
        String res = this.getType() + "{";

        if (DEBUG_MODE) {
            res += "From:" + sourceAddress + ":" + sourcePort + " " +
                "To:" + destAddress + ":" + destPort + " ";
        }
        res += "destId:" + destId + " " +
            "path" + path +
            "}";

        return res;
    }
}
