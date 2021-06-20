package sender;

import chord.ChordController;
import file.DigestFile;
import message.*;
import state.State;
import utils.Pair;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MessageHandler {
    // private final SockThread sock;
    private final ChordController controller;
    InetAddress address;
    Integer port;
    private final ConcurrentMap<Pair<String, Integer>, CompletableFuture<byte[]>> receivedChunks;

    public MessageHandler(SockThread sock, ChordController chordController) {
        this.controller = chordController;
        this.receivedChunks = new ConcurrentHashMap<>();
        this.address = sock.getAddress();
        this.port = sock.getPort();
    }

    public void addChunkFuture(String fileId, int currChunk, CompletableFuture<byte[]> fut) {
        this.receivedChunks.put(new Pair<>(fileId, currChunk), fut);
    }

    public void removeAllChunkFuture(String fileId) {
        for (var entry : this.receivedChunks.entrySet())
            if (entry.getKey().p1.equals(fileId))
                this.receivedChunks.remove(entry.getKey(), entry.getValue());
    }

    private void handleMsg(PutChunkMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        if (message.hasNoSource()) { // We are responsible for this message, mark us as responsible
            System.out.println("I am responsible for " + message);
            message.setSource(this.controller);
        }

        boolean iStoredTheChunk = false;
        int chunkId = -1;
        synchronized (State.st) {
            // always register the existence of this file except when we want to reinit backup protocol
            State.st.addFileEntry(message.getFileId(), message.getReplication());
            State.st.declareChunk(message.getFileId(), message.getChunkNo());
            boolean isInitiator = State.st.isInitiator(message.getFileId());

            if (!isInitiator) {
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

                        // Add sequence number
                        chunkId = DigestFile.getId(message.getFileId(), message.getChunkNo());
                        State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(),
                                chunkId, message.getSeqNumber());
                        iStoredTheChunk = true;
                    }
                } else {
                    // Update sequence number
                    chunkId = DigestFile.getId(message.getFileId(), message.getChunkNo());
                    State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), message.getSeqNumber());
                    iStoredTheChunk = true;
                }
            }
        }

        // I am responsible and i stored the message
        if (iStoredTheChunk && message.getSeqNumber() == message.getReplication()) {
            System.out.println("I am responsible for chunk " + message.getFileId() + " " + message.getChunkNo());
            message.setSource(this.controller);
        }

        // send STORED reply message to our predecessor if we stored the chunk/already had it
        if (iStoredTheChunk) {
            StoredMsg response = new StoredMsg(message.getFileId(), this.address, this.port,
                    message.getChunkNo(), chunkId);
            InetAddress address;
            int port;
            try {
                address = this.controller.getChordNode().getPredecessor().getAddress();
                port = this.controller.getChordNode().getPredecessor().getPort();
            } catch (RemoteException | NullPointerException e) {
                System.err.println("Couldn't find predecessor to send him a STORED reply");
                return;
            }
            this.controller.sendDirectly(response, address, port);
            message.decreaseCurrentRep(); // Update current rep in putchunk chain
        }

        // Propagate putchunks through successors
        // We don't need to resend the putchunk message further, we are last in the chain
        if (message.getSeqNumber() == 0)
            return;

        this.controller.sendToSucc(message);
    }

    private void handleMsg(StoredMsg message) {
        synchronized (State.st) {
            State.st.addSuccChunk(message.getFileId(), message.getChunkNo(), message.getChunkId());
        }
    }

    private void handleMsg(ChunkMsg message) {
        Pair<String, Integer> msgChunk = new Pair<>(message.getFileId(), message.getChunkNo());
        if (this.receivedChunks.containsKey(msgChunk)) {
            this.receivedChunks.get(msgChunk).complete(message.getChunk());
        }
    }

    private void handleMsg(DeleteMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        synchronized (State.st) {
            DigestFile.deleteFile(message.getFileId());
            State.st.removeSuccChunk(message.getFileId());
        }

        this.controller.sendToSucc(message);
    }

    private void handleMsg(GetChunkMsg message) {
        if (message.destAddrKnown() && message.hasLooped() && this.messageSentByUs(message)) {
            System.out.println("\t\tMessage looped through network " + message);
            // Mark getchunk has unsuccessful
            var filePair = new Pair<>(message.getFileId(), message.getChunkNo());
            if (this.receivedChunks.containsKey(filePair))
                this.receivedChunks.get(filePair).complete(null);
            return; // We sent this message and it has looped through the network
        }

        synchronized (State.st) {
            if (State.st.amIStoringChunk(message.getFileId(), message.getChunkNo())) {
                ChunkMsg response = new ChunkMsg(message.getFileId(), message.getChunkNo(),
                        this.address, this.port, null);
                response.setSource(this.controller);
                this.controller.sendDirectly(response, message.getSourceAddress(), message.getSourcePort());
                return;
            }
        }

        if (message.destAddrKnown()) { // Sent to us
            if (message.getResponsible() == -1) { // First time we are receiving the message
                message.setResponsible(this.controller.getId()); // Set us as responsible
            } else if(message.getResponsible() == this.controller.getId()) { // The message has looped, send to source saying that the chunk isn't in the network
                message.setLooped();
                this.controller.sendDirectly(message, message.getSourceAddress(), message.getSourcePort());
                return;
            }
        }

        // Resend to next node in ring
        this.controller.sendToSucc(message);
    }

    private void handleMsg(RemovedMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        if (message.hasNoSource()) // We are responsible for this message, mark us as responsible
            message.setSource(this.controller);


        // Our successor told us to that he is no longer storing this chunk
        if (message.isToPredecessor()) {
            State.st.removeSuccChunk(message.getFileId(), message.getChunkNo());
            // We want to stop here because if we have the file a second removed message will be sent to us
            // and then we will restart the backup protocol
            return;
        }

        boolean isStoringChunk, isInitiator;
        int replication = -1;
        String filePath = "";
        byte[] c = null;
        synchronized (State.st) {
            isStoringChunk = State.st.amIStoringChunk(message.getFileId(), message.getChunkNo());
            isInitiator = State.st.isInitiator(message.getFileId());
            if (isInitiator || isStoringChunk) {
                if (isInitiator) filePath = State.st.getFileInfo(message.getFileId()).getFilePath();

                replication = State.st.getFileInfo(message.getFileId()).getDesiredRep();
                // Initiate backup protocol again
                try {
                    if (isStoringChunk) {
                        c = DigestFile.readChunk(message.getFileId(), message.getChunkNo());
                    } else {
                        c = DigestFile.divideFileChunk(filePath, message.getChunkNo());
                    }
                } catch (IOException e) {
                    System.out.println("couldn't read supposed store chunk " +
                            message.getFileId() + " " + message.getChunkNo());
                    return;
                }
            }
        }

        if (isInitiator || isStoringChunk) { // If we have the file that got deleted
            // We don't give the source so that the responsible node for this chunk fills it when it receives the msg
            int chunkId = DigestFile.getId(message.getFileId(), message.getChunkNo());
            this.controller.send(new PutChunkMsg(message.getFileId(), message.getChunkNo(), c, replication, chunkId));
        } else {
            // Resend to next node in ring
            this.controller.sendToSucc(message);
        }
    }

    private boolean messageSentByUs(Message message) {
        if (message.getSourcePort() == -1) return false;
        return (message.getSourcePort() == this.controller.getPort() &&
                message.getSourceAddress().equals(this.controller.getAddress()) &&
                message.destAddrKnown());
    }

    public void handleMessage(Message message) {
//        TODO i don't think we want this here, we want some message to loop through the ring.
//         Handled by each message differently
//        if (this.messageSentByUs(message)) {
//            System.out.println("\t\tMessage looped through network " + message);
//            return; // We sent this message and it has looped through the network
//        }

        if (PutChunkMsg.class.equals(message.getClass())) {
            handleMsg((PutChunkMsg) message);
        } else if (StoredMsg.class.equals(message.getClass())) {
            handleMsg((StoredMsg) message);
        } else if (DeleteMsg.class.equals(message.getClass())) {
            handleMsg((DeleteMsg) message);
        } else if (GetChunkMsg.class.equals(message.getClass())) {
            handleMsg((GetChunkMsg) message);
        } else if (ChunkMsg.class.equals(message.getClass())) {
            handleMsg((ChunkMsg) message);
        } else if (RemovedMsg.class.equals(message.getClass())) {
            handleMsg((RemovedMsg) message);
        }
    }
}
