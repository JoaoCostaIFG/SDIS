package message;

import static java.lang.Integer.parseInt;

public class MessageCreator {
    final String protocolVersion;

    public MessageCreator(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Message createMessage(String[] header, byte[] body) throws NoSuchMessage {
        Message res = null;
        switch (header[Message.typeField]) {
            // Backup Subprotocol
            case (PutChunkMsg.type):
                res = new PutChunkMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField],
                        parseInt(header[Message.chunkField]),
                        parseInt(header[Message.replicationField]),
                        body);
                break;
            case (StoredMsg.type):
                res = new StoredMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField],
                        parseInt(header[Message.chunkField]));
                break;
            // Restore Subprotocol
            case (GetChunkMsg.type):
                res = new GetChunkMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField],
                        parseInt(header[Message.chunkField]));
                break;
            case (ChunkMsg.type):
                res = new ChunkMsg(header[Message.versionField], header[Message.idField],
                        header[Message.fileField],
                        parseInt(header[Message.chunkField]),
                        body);
                break;
            // File deletion Subprotocol
            case (DeleteMsg.type):
                res = new DeleteMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField]);
                break;
            // Space reclaim Subprotocol
            case (RemovedMsg.type):
                res = new RemovedMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField],
                        parseInt(header[Message.chunkField]));
                break;
            case (IDeletedMsg.type):
                res = new IDeletedMsg(header[Message.versionField],
                        header[Message.idField],
                        header[Message.fileField]);
                break;
            default:
                throw new NoSuchMessage(header[Message.typeField]);
        }

        return res;
    }
}
