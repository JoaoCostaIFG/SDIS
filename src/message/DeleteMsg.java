package message;

import java.net.InetAddress;

public class DeleteMsg extends Message {
    public static final String type = "DELETE";

    public DeleteMsg(String fileId, InetAddress sourceDest, int sourcePort, int destId) {
        super(fileId, sourceDest, sourcePort, destId);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
