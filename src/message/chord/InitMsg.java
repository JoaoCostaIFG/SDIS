package message.chord;

import message.Message;

public class InitMsg extends Message {
    public InitMsg(String version, String id, String fileId) {
        super(version, id, fileId);
    }

    @Override
    public String getSockName() {
        return null;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public int getHeaderLen() {
        return 0;
    }
}
