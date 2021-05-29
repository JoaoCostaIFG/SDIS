package sender;

import chord.ChordNode;
import file.DigestFile;
import message.*;
import state.State;

import java.io.IOException;
import java.rmi.RemoteException;

public class MessageHandler {
    private final SockThread sock;
    private final ChordNode chordNode;

    public MessageHandler(SockThread sock, ChordNode chordNode) {
        this.sock = sock;
        this.chordNode = chordNode;
    }

    private void handleMsg(PutChunkMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        boolean iStoredTheChunk = false;
        synchronized (State.st) {
            // always register the existence of this file
            State.st.addFileEntry(message.getFileId(), message.getReplication());
            State.st.declareChunk(message.getFileId(), message.getChunkNo());

            // do not store duplicated chunks or if we surpass storage space or if we are initiator
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo()) && !State.getState().isInitiator(message.getFileId())) {
                if (State.st.updateStorageSize(message.getChunk().length)) {
                    try {
                        DigestFile.writeChunk(message.getFileId(), message.getChunkNo(),
                                message.getChunk(), message.getChunk().length);
                    } catch (IOException e) {
                        e.printStackTrace();
                        State.st.updateStorageSize(-message.getChunk().length);
                    }

                    // Add sequence number
                    State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), message.getSeqNumber());
                    iStoredTheChunk = true;
                }
            } else {
                // Update sequence number
                State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), message.getSeqNumber());
                iStoredTheChunk = true;
            }
        }

        // I am responsible and i stored the message
        if (iStoredTheChunk && message.getSeqNumber() == message.getReplication()) {
            System.out.println("I am responsible for chunk " + message.getFileId() + " " + message.getChunkNo());
            message.setSource(this.chordNode); // 'Subscribe' to all stored messages
        }

        // send STORED reply message if we stored the chunk/already had it
        if (iStoredTheChunk) { // TODO remover os stores maybe?
                StoredMsg response = new StoredMsg(message.getFileId(), this.sock.getAddress(), this.sock.getPort(),
                        message.getChunkNo());
                response.setDest(message.getSourceAddress(), message.getSourcePort());
                this.sock.send(response);
                message.decreaseCurrentRep(); // Update current rep in putchunk chain
        }

        // Propagate putchunks through successors
        if (message.getSeqNumber() == 0) // We don't need to resend the putchunk message further, we are last in the chain
            return;

        try {
            message.setDest(chordNode.getSuccessor());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        message.setDestId(null); // We don't need the chord ring to hop to the dest, we already know it
        this.sock.send(message);
    }

    private void handleMsg(StoredMsg message) {
        synchronized (State.st) {
            // State.st.incrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
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
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        synchronized (State.st) {
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo())) {
                // Resend to next node in ring
                try {
                    message.setDest(chordNode.getSuccessor());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                // We don't need the chord ring to hop to the dest, we just need to hop it to next successor successively
                message.setDestId(null);
                this.sock.send(message);
                return;
            }
        }

        ChunkMsg response = new ChunkMsg(message.getFileId(), message.getChunkNo(), this.sock.getAddress(),
                this.sock.getPort(), null);
        response.setDest(message.getSourceAddress(), message.getSourcePort());
        response.setSource(this.chordNode);
        this.sock.send(response);
    }

    private void handleMsg(RemovedMsg message) {
        int repDegree;
        boolean amInitiator;
//        synchronized (State.st) {
//            State.st.decrementChunkDeg(message.getFileId(), message.getChunkNo(), message.getSenderId());
//            if (State.st.isChunkOk(message.getFileId(), message.getChunkNo()))
//                return;
//            // we can only serve a chunk if:
//            // we are storing it or we are the initiator
//            amInitiator = State.st.isInitiator(message.getFileId());
//            if (!amInitiator && !State.st.amIStoringChunk(message.getFileId(), message.getChunkNo()))
//                return;
//            repDegree = State.st.getFileDeg(message.getFileId());
//        }

//        try {
//            byte[] chunk;
//            if (amInitiator) {
//                chunk = DigestFile.divideFileChunk(State.st.getFileInfo(message.getFileId()).getFilePath(),
//                        message.getChunkNo());
//            } else {
//                chunk = DigestFile.readChunk(message.getFileId(), message.getChunkNo());
//            }

//            PutChunkMsg putChunkMsg = new PutChunkMsg(this.protocolVersion, this.selfID,
//                    message.getFileId(), message.getChunkNo(), repDegree, chunk);
//            RemovedPutchunkSender removedPutchunkSender = new RemovedPutchunkSender(this.MDBSock, putChunkMsg,
//                    this);
//            removedPutchunkSender.run();
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.err.println("Failed constructing reply for " + message.getType());
//        }
    }

    private boolean messageSentByUs(Message message) {
        return (message.getSourcePort() == this.chordNode.getPort() &&
                message.getSourceAddress().equals(this.chordNode.getAddress()) &&
                message.destAddrKnown());
    }

    // TODO verify message came from the socket?
    public void handleMessage(Message message) {
//        TODO i don't think we want this here, we want some message to loop through the ring. Handled by each message differently
//        if (this.messageSentByUs(message)) {
//            System.out.println("\t\tMessage looped through network " + message);
//            return; // We sent this message and it has looped through the network TODO if getchunk say that it failed
//        }

        if (PutChunkMsg.class.equals(message.getClass())) {
            handleMsg((PutChunkMsg) message);
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
