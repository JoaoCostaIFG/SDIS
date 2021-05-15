package message.chord;


import chord.ChordNode;

public class GetSuccMsg extends ChordMessage {
    public static final String type = "GETSUCC";
    private int nodeId;

    public GetSuccMsg(ChordNode chordNode) {
        this(chordNode.getAddress(), chordNode.getPort(), chordNode.getId());
    }

    public GetSuccMsg(String address, int port, int id) {
        super(address, port);
        this.nodeId = id;
    }

    public int getNodeId() {
        return nodeId;
    }

    @Override
    public String getType() {
        return type;
    }
}
