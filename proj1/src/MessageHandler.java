import File.DigestFile;
import Message.Message;
import Message.ChunkBackupMsg;
import Message.ChunkMsg;
import Message.ChunkStoredMsg;
import Message.GetChunkMsg;
import Message.FileDeletionMsg;
import Message.NoSuchMessage;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static Message.MessageCreator.createMessage;

public class MessageHandler {
    public final int maxBackofMs = 401;
    private final String selfID;
    private final String protocolVersion;
    private final SockThread MCSock;
    private final SockThread MDBSock;
    private final SockThread MDRSock;

    public MessageHandler(String selfID, String protocolVersion, SockThread MCSock, SockThread MDBSock, SockThread MDRSock) {
        this.selfID = selfID;
        this.protocolVersion = protocolVersion;
        this.MCSock = MCSock;
        this.MDBSock = MDBSock;
        this.MDRSock = MDRSock;
        this.MCSock.setHandler(this);
        this.MDBSock.setHandler(this);
        this.MDRSock.setHandler(this);
    }

    public void handleMessage(SockThread sock, String received) {
        final String[] receivedFields = received.split(Message.CRLF, 3);
        final String[] header = receivedFields[0].split(" ");
        final String body = (receivedFields.length > 2) ? receivedFields[2] : null;
        System.err.println("AAAAAAAAAAAAAAA: " + received.length());

        if (header[Message.idField].equals(this.selfID)) {
            System.out.println("We were the ones that sent this message. Skipping..");
            return;
        }

        // construct the reply
        Message message = null;
        try {
            message = createMessage(header, body);
        } catch (NoSuchMessage noSuchMessage) {
            System.err.println("No Such message " + header[Message.typeField]);
            return;
        }
        assert message != null;

        try {
            System.out.println("Received: " + Arrays.toString(header));
            switch (message.getType()) {
                case ChunkBackupMsg.type:
                    ChunkBackupMsg backupMsg = (ChunkBackupMsg) message;
                    try {
                        DigestFile.writeChunk(backupMsg.getFileId() + File.separator + backupMsg.getChunkNo(),
                                backupMsg.getChunk(), backupMsg.getChunk().length);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    // send STORED reply message
                    Message response = new ChunkStoredMsg(this.protocolVersion, this.selfID,
                            backupMsg.getFileId(), backupMsg.getChunkNo());
                    Random random = new Random();
                    this.MDBSock.send(response, random.nextInt(maxBackofMs));
                    break;
                case ChunkStoredMsg.type:
                    break;
                case FileDeletionMsg.type:
                    // TODO delete file here
                    FileDeletionMsg delMsg = (FileDeletionMsg) message;
                    DigestFile.deleteFile(delMsg.getFileId());
                    return;  // file deletion doesn't send a reply
                case GetChunkMsg.type:
                    GetChunkMsg getChunkMsg = (GetChunkMsg) message;
                    if (DigestFile.hasChunk(getChunkMsg.getFileId(), getChunkMsg.getChunkNo())) {
                        // TODO Thread here
                        response = new ChunkMsg(this.protocolVersion, this.selfID,
                                getChunkMsg.getFileId(), getChunkMsg.getChunkNo());
                        this.MCSock.send(response, new Random().nextInt(401));
                    }
                    break;
                case ChunkMsg.type:
                    ChunkMsg chunkMsg = (ChunkMsg) message;
                    DigestFile.writeChunk(chunkMsg, chunkMsg.getFileId(), chunkMsg.getChunkNo());
                default:
                    // unreachable
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed constructing reply for " + message.getType());
        }
    }
}
