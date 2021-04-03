import file.DigestFile;
import message.*;
import utils.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static message.MessageCreator.createMessage;

public class MessageHandler {
    public final int maxBackofMs = 401;
    private final String selfID;
    private final String protocolVersion;
    private final SockThread MCSock;
    private final SockThread MDBSock;
    private final SockThread MDRSock;
    private List<Observer> observers;

    public MessageHandler(String selfID, String protocolVersion, SockThread MCSock, SockThread MDBSock, SockThread MDRSock) {
        this.selfID = selfID;
        this.protocolVersion = protocolVersion;
        this.MCSock = MCSock;
        this.MDBSock = MDBSock;
        this.MDRSock = MDRSock;
        this.MCSock.setHandler(this);
        this.MDBSock.setHandler(this);
        this.MDRSock.setHandler(this);
        this.observers = new ArrayList<>();
        DigestFile.importMap();
    }

    public void addObserver(Observer obs) {
        this.observers.add(obs);
    }

    public void rmObserver(Observer obs) {
        this.observers.remove(obs);
    }

    public void saveMap() {
        try {
            DigestFile.exportMap();
        } catch (IOException e) {
            e.printStackTrace(); // TODO handle this?
        }
    }

    private boolean hasSpace(int newSize) {
        return Proj1.maxDiskSpaceB < 0 || (DigestFile.getStorageSize() + newSize <= Proj1.maxDiskSpaceB);
    }

    public void handleMessage(SockThread sock, String received) {
        final String[] receivedFields = received.split(Message.CRLF, 3);
        final String[] header = receivedFields[0].split(" ");
        final String body = (receivedFields.length > 2) ? receivedFields[2] : null;

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

        // notify observers
        for (Observer obs : this.observers) {
            obs.notify(message);
        }

        try {
            System.out.println("Received: " + Arrays.toString(header));
            Message response;
            switch (message.getType()) {
                case PutChunkMsg.type:
                    PutChunkMsg backupMsg = (PutChunkMsg) message;
                    // do not store duplicated chunks
                    if (DigestFile.hasChunk(backupMsg.getFileId(), backupMsg.getChunkNo())) break;
                    // if we surpass storage space
                    if (!this.hasSpace(backupMsg.getChunk().length)) break;

                    try {
                        DigestFile.writeChunk(backupMsg.getFileId() + File.separator + backupMsg.getChunkNo(),
                                backupMsg.getChunk(), backupMsg.getChunk().length);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    if (!DigestFile.replicationDegMap.containsKey(backupMsg.getFileId()))
                        DigestFile.addFileEntry(backupMsg.getFileId(), backupMsg.getReplication());
                    DigestFile.incrementChunkDeg(backupMsg.getFileId(), backupMsg.getChunkNo());
                    // send STORED reply message
                    response = new StoredMsg(this.protocolVersion, this.selfID,
                            backupMsg.getFileId(), backupMsg.getChunkNo());

                    StoredSender storedSender = new StoredSender(this.MCSock, (StoredMsg) response, this);
                    storedSender.run();
                    // unsub MDB when storage is full
                    if (DigestFile.getStorageSize() == Proj1.maxDiskSpaceB) this.MDBSock.leave();
                    break;
                case StoredMsg.type:
                    StoredMsg storedMsg = (StoredMsg) message;
                    DigestFile.incrementChunkDeg(storedMsg.getFileId(), storedMsg.getChunkNo());
                    break;
                case DeleteMsg.type:
                    DeleteMsg delMsg = (DeleteMsg) message;
                    DigestFile.deleteFile(delMsg.getFileId());

                    // sub MDB when storage is not full
                    if (DigestFile.getStorageSize() < Proj1.maxDiskSpaceB)
                        this.MDBSock.join();
                    return;  // IMP file deletion doesn't send a reply
                case GetChunkMsg.type:
                    GetChunkMsg getChunkMsg = (GetChunkMsg) message;
                    if (DigestFile.hasChunk(getChunkMsg.getFileId(), getChunkMsg.getChunkNo())) {
                        response = new ChunkMsg(this.protocolVersion, this.selfID,
                                getChunkMsg.getFileId(), getChunkMsg.getChunkNo());
                        ChunkSender chunkSender = new ChunkSender(this.MDRSock, (ChunkMsg) response, this);
                        chunkSender.run();
                    }
                    break;
                case ChunkMsg.type:
                    ChunkMsg chunkMsg = (ChunkMsg) message;
                    // TODO Check if we are expecting this chunk msg
                    DigestFile.writeChunk(chunkMsg, chunkMsg.getFileId(), chunkMsg.getChunkNo());
                    break;
                case RemovedMsg.type:
                    RemovedMsg removedMsg = (RemovedMsg) message;
                    DigestFile.decreaseChunkDeg(removedMsg.getFileId(), removedMsg.getChunkNo());
                    // TODO initiate the chunk backup subprotocol after random delay
                    // TODO if during this time, we get a PUTCHUNK for this chunk => back off
                    break;
                default:
                    // unreachable
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed constructing reply for " + message.getType());
        }
    }

    private void sendPutChunk(String fileId, Integer chunkNo) {
        
    }
}
