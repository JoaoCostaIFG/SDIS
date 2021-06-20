package sender;

import file.DigestFile;
import message.*;
import state.State;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandler {
    private final String selfID;
    private final String protocolVersion;
    private final MessageCreator messageCreator;
    private final SockThread MCSock;
    private final SockThread MDBSock;
    private final SockThread MDRSock;
    private final ConcurrentHashMap<Observer, Boolean> observers;

    public MessageHandler(String selfID, String protocolVersion, SockThread MCSock, SockThread MDBSock, SockThread MDRSock) {
        this.selfID = selfID;
        this.protocolVersion = protocolVersion;
        this.messageCreator = new MessageCreator(protocolVersion);
        this.MCSock = MCSock;
        this.MDBSock = MDBSock;
        this.MDRSock = MDRSock;
        this.MCSock.setHandler(this);
        this.MDBSock.setHandler(this);
        this.MDRSock.setHandler(this);
        this.observers = new ConcurrentHashMap<>();
    }

    public void addObserver(Observer obs) {
        this.observers.put(obs, false);
    }

    public void rmObserver(Observer obs) {
        this.observers.remove(obs);
    }

    private void handlePutChunkMsg(PutChunkMsg message) {
        boolean iStoredTheChunk = false;
        synchronized (State.st) {
            // do not handle files we initiated the backup of
            if (State.st.isInitiator(message.getFileId())) return;

            // always register the existence of this file
            State.st.addFileEntry(message.getFileId(), message.getReplication());
            State.st.declareChunk(message.getFileId(), message.getChunkNo());

            // do not store duplicated chunks or if we surpass storage space
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo())) {
                if (State.st.updateStorageSize(message.getChunk().length)) {
                    try {
                        DigestFile.writeChunk(message.getFileId(), message.getChunkNo(),
                                message.getChunk(), message.getChunk().length);
                    } catch (IOException e) {
                        e.printStackTrace();
                        State.st.updateStorageSize(-message.getChunk().length);
                    }

                    // Add self to map Entry
                    State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), this.selfID);
                    State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), true);
                    iStoredTheChunk = true;

                    // unsub MDB when storage is full
                    if (this.protocolVersion.equals("2.0")) {
                        if (State.st.isStorageFull()) this.MDBSock.leave();
                        else this.MDBSock.join();
                    }
                }
            } else {
                iStoredTheChunk = true;
            }
        }

        // send STORED reply message if we stored the chunk/already had it
        if (iStoredTheChunk) {
            StoredMsg response = new StoredMsg(this.protocolVersion, this.selfID,
                    message.getFileId(), message.getChunkNo());
            StoredSender storedSender = new StoredSender(this.MCSock, response, this);
            storedSender.run();
        }
    }

    private void handleStoredMsg(StoredMsg message) {
        synchronized (State.st) {
            State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
        }
    }

    private void handleDeleteMsg(DeleteMsg message) {
        boolean sendIDeleted;
        synchronized (State.st) {
            // delete the file on the file system
            // also updates state entry and space filled
            sendIDeleted = DigestFile.deleteFile(message.getFileId());

            if (this.protocolVersion.equals("2.0")) {
                // unsub MDB when storage is not full
                if (State.st.isStorageFull()) this.MDBSock.leave();
                else this.MDBSock.join();
            }
        }

        // send IDELETED when we are 2.0 and the DELETE was 2.0
        if (sendIDeleted && this.protocolVersion.equals("2.0") && message.getVersion().equals("2.0")) {
            IDeletedMsg response = new IDeletedMsg(this.protocolVersion, this.selfID, message.getFileId());
            IDeletedSender iDeletedSender = new IDeletedSender(this.MCSock, response, this);
            iDeletedSender.run();
        }
    }

    private void handleIDeletedMsg(IDeletedMsg message) {
        // only handle IDELETED messages if we are 2.0
        if (this.protocolVersion.equals("2.0")) {
            synchronized (State.st) {
                State.st.removeUndeletedPair(message.getSenderId(), message.getFileId());
            }
        }
    }

    private void handleGetChunkMsg(GetChunkMsg message) {
        synchronized (State.st) {
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo()))
                return;
        }

        ChunkMsg response = new ChunkMsg(this.protocolVersion, this.selfID,
                message.getFileId(), message.getChunkNo());
        MessageSender<? extends Message> chunkSender;
        if (this.protocolVersion.equals("2.0") && message.getVersion().equals("2.0")) {
            chunkSender = new ChunkTCPSender(this.MDRSock, response, this);
        } else {
            chunkSender = new ChunkSender(this.MDRSock, response, this);
        }
        chunkSender.run();
    }

    private void handleRemovedMsg(RemovedMsg message) {
        int repDegree;
        boolean amInitiator;
        synchronized (State.st) {
            State.st.decrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
            if (State.st.isChunkOk(message.getFileId(), message.getChunkNo()))
                return;
            // we can only serve a chunk if:
            // we are storing it or we are the initiator
            amInitiator = State.st.isInitiator(message.getFileId());
            if (!amInitiator && !State.st.amIStoringChunk(message.getFileId(), message.getChunkNo()))
                return;
            repDegree = State.st.getFileDeg(message.getFileId());
        }

        try {
            byte[] chunk;
            if (amInitiator) {
                chunk = DigestFile.divideFileChunk(State.st.getFileInfo(message.getFileId()).getFilePath(),
                        message.getChunkNo());
            } else {
                chunk = DigestFile.readChunk(message.getFileId(), message.getChunkNo());
            }

            PutChunkMsg putChunkMsg = new PutChunkMsg(this.protocolVersion, this.selfID,
                    message.getFileId(), message.getChunkNo(), repDegree, chunk);
            RemovedPutchunkSender removedPutchunkSender = new RemovedPutchunkSender(this.MDBSock, putChunkMsg,
                    this);
            removedPutchunkSender.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed constructing reply for " + message.getType());
        }
    }

    // TODO verify message came from the socket?
    public void handleMessage(String sockName, byte[] receivedData) {
        int crlfCount = 0;
        int headerCutoff;
        for (headerCutoff = 0; headerCutoff < receivedData.length - 1; ++headerCutoff) {
            if (receivedData[headerCutoff] == 0xD && receivedData[headerCutoff + 1] == 0xA)
                ++crlfCount;
            if (crlfCount == 2)
                break;
        }

        final String[] header = new String(receivedData, 0, headerCutoff - 2).split(" ");
        byte[] body = (receivedData.length <= headerCutoff + 2) ?
                new byte[0] :
                Arrays.copyOfRange(receivedData, headerCutoff + 2, receivedData.length);

        // skip our own messages (multicast)
        if (header[Message.idField].equals(this.selfID)) {
            // System.out.println("We were the ones that sent this message. Skipping...");
            return;
        }

        // construct the reply
        Message message;
        try {
            message = messageCreator.createMessage(header, body);
        } catch (NoSuchMessage noSuchMessage) {
            System.err.println("No Such message " + header[Message.typeField]);
            return;
        }
        assert message != null;

        // skip message that came from the wrong socket.
        if (!message.getSockName().equals(sockName)) {
            System.err.println("Skipping message that came from the wrong socket.");
        }

        System.out.println("\tReceived: " + message);
        // notify observers
        for (Observer obs : this.observers.keySet()) {
            obs.notify(message);
        }

        switch (message.getType()) {
            case PutChunkMsg.type:
                handlePutChunkMsg((PutChunkMsg) message);
                break;
            case StoredMsg.type:
                handleStoredMsg((StoredMsg) message);
                break;
            case DeleteMsg.type:
                handleDeleteMsg((DeleteMsg) message);
                break;
            case IDeletedMsg.type:
                handleIDeletedMsg((IDeletedMsg) message);
                break;
            case GetChunkMsg.type:
                handleGetChunkMsg((GetChunkMsg) message);
                break;
            case ChunkMsg.type:
                // skip
                break;
            case RemovedMsg.type:
                handleRemovedMsg((RemovedMsg) message);
                break;
            default:
                // unreachable
                break;
        }

        // see if guy who sents the message has to remove some file
        if (this.protocolVersion.equals("2.0")) {
            // inform him of deletion if the peer supports it
            Set<String> files = State.st.getFilesUndeletedByPeer(message.getSenderId());
            if (files != null) {
                for (String fileId : files) {
                    DeleteMsg deleteMsg = new DeleteMsg(this.protocolVersion, this.selfID, fileId);
                    this.MCSock.send(deleteMsg);
                }
            }
            // just get rid of his reference otherwise
            if (!message.getVersion().equals("2.0")) {
                State.st.ignorePeerDeletedFiles(message.getSenderId());
            }
        }
    }
}
