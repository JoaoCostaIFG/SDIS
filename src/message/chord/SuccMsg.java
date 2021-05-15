package message.chord;

public class SuccMsg extends ChordMessage {
    private final String succAddress;
    private final int succPort;

    public SuccMsg(String address, int port, String succAddress, int succPort) {
        super(address, port);
        this.succAddress = succAddress;
        this.succPort = succPort;
    }
}
