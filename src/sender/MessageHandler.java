package sender;

import file.DigestFile;
import message.Message;
import message.chord.GetSuccMsg;
import message.file.*;
import state.State;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandler {
    private final Integer selfID;
    private final SockThread sock;
    private final ConcurrentHashMap<Observer, Boolean> observers;

    public MessageHandler(int id, SockThread sock) {
        this.selfID = id;
        this.sock = sock;
        this.sock.setHandler(this);
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
                    State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), this.selfID.toString());
                    State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), true);
                    iStoredTheChunk = true;
                }
            } else {
                iStoredTheChunk = true;
            }
        }

        // send STORED reply message if we stored the chunk/already had it
        if (iStoredTheChunk) {
//            StoredMsg response = new StoredMsg(this.protocolVersion, this.selfID,
//                    message.getFileId(), message.getChunkNo());
//            StoredSender storedSender = new StoredSender(this.MCSock, response, this);
//            storedSender.run();
        }
    }

    private void handleStoredMsg(StoredMsg message) {
        synchronized (State.st) {
            State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
        }
    }

    private void handleDeleteMsg(DeleteMsg message) {
        synchronized (State.st) {
            // delete the file on the file system
            // also updates state entry and space filled
            DigestFile.deleteFile(message.getFileId());
        }
    }

    private void handleGetChunkMsg(GetChunkMsg message) {
        synchronized (State.st) {
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo()))
                return;
        }

//        ChunkMsg response = new ChunkMsg(this.protocolVersion, this.selfID,
//                message.getFileId(), message.getChunkNo());
//        MessageSender<? extends Message> chunkSender;
//        chunkSender = new ChunkSender(this.MDRSock, response, this);
//        chunkSender.run();
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

//            PutChunkMsg putChunkMsg = new PutChunkMsg(this.protocolVersion, this.selfID,
//                    message.getFileId(), message.getChunkNo(), repDegree, chunk);
//            RemovedPutchunkSender removedPutchunkSender = new RemovedPutchunkSender(this.MDBSock, putChunkMsg,
//                    this);
//            removedPutchunkSender.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed constructing reply for " + message.getType());
        }
    }

    // TODO verify message came from the socket?
    public void handleMessage(Message message) {
        // skip our own messages (multicast)
        if (message.getSenderId().equals(this.selfID)) {
            System.out.println("We were the ones that sent this message. Skipping...");
            return;
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
            case GetChunkMsg.type:
                handleGetChunkMsg((GetChunkMsg) message);
                break;
            case ChunkMsg.type:
                // skip
                break;
            case RemovedMsg.type:
                handleRemovedMsg((RemovedMsg) message);
                break;
            case GetSuccMsg.type:
                System.err.println("OPA!");
                break;
            default:
                // unreachable
                break;
        }
    }
}
