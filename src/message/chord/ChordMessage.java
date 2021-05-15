package message.chord;

import message.Message;

public abstract class ChordMessage extends Message {
    public static final String type = "CHORDMSG";
    private final int port;
    private final String address;

    public ChordMessage(String address, int port) {
        super("chord", "-1");
        this.address = address;
        this.port = port;
    }

    @Override
    public String getType() {
        return type;
    }
}
