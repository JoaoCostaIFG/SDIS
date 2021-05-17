package sender;

import file.DigestFile;
import message.*;
import state.State;

import java.io.IOException;
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

    private void handleMsg(PutChunkMsg message) {
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

    private void handleMsg(StoredMsg message) {
        synchronized (State.st) {
            State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
        }
    }

    private void handleMsg(DeleteMsg message) {
        synchronized (State.st) {
            // delete the file on the file system
            // also updates state entry and space filled
            DigestFile.deleteFile(message.getFileId());
        }
    }

    private void handleMsg(GetChunkMsg message) {
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

    private void handleMsg(RemovedMsg message) {
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
        System.out.println("\tReceived: " + message);
        // notify observers
        for (Observer obs : this.observers.keySet()) {
            obs.notify(message);
        }

        // IMP we want to skip handling the message only after we informed the msg to the chord node
        if (!message.getDestId().equals(this.selfID)) {
            System.out.println("This message isn't for us");
            return;
        }

        // unreachable
        if (PutChunkMsg.class.equals(message.getClass())) {
//            handleMsg((PutChunkMsg) message);
        } else if (StoredMsg.class.equals(message.getClass())) {
            handleMsg((StoredMsg) message);
        } else if (DeleteMsg.class.equals(message.getClass())) {
            handleMsg((DeleteMsg) message);
        } else if (GetChunkMsg.class.equals(message.getClass())) {
            handleMsg((GetChunkMsg) message);
        } else if (ChunkMsg.class.equals(message.getClass())) { // skoiop
        } else if (RemovedMsg.class.equals(message.getClass())) {
            handleMsg((RemovedMsg) message);
        }
    }
}
