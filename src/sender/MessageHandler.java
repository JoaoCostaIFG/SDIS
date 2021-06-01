package sender;

import chord.ChordNode;
import file.DigestFile;
import message.*;
import state.State;
import utils.Pair;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MessageHandler {
    // private final SockThread sock;
    private final ChordNode chordNode;
    InetAddress address;
    Integer port;
    private final ConcurrentMap<Pair<String, Integer>, CompletableFuture<byte[]>> receivedChunks;

    public MessageHandler(SockThread sock, ChordNode chordNode) {
        this.chordNode = chordNode;
        this.receivedChunks = new ConcurrentHashMap<>();
        this.address = sock.getAddress();
        this.port = sock.getPort();
    }

    public void addChunkFuture(String fileId, int currChunk, CompletableFuture<byte[]> fut) {
        this.receivedChunks.put(new Pair<>(fileId, currChunk), fut);
    }

    private void handleMsg(PutChunkMsg message) {
        if (message.hasNoSource()) // We are responsible for this message, mark us as responsible
            message.setSource(this.chordNode);

        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

        boolean iStoredTheChunk = false, reInitBackup = false, isInitiator;
        String filePath = "";
        int chunkId = -1;
        synchronized (State.st) {
            // always register the existence of this file except when we want to reinit backup protocol
            if (!message.reInitiateBackup())
                State.st.addFileEntry(message.getFileId(), message.getReplication());
            State.st.declareChunk(message.getFileId(), message.getChunkNo());
            isInitiator = State.st.isInitiator(message.getFileId());
            if (!isInitiator) {
                 // do not store duplicated chunks or if we surpass storage space
                 if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo())) {
                     if (!message.reInitiateBackup() && // We want to redirect to succ if we don't have the chunk if it is to be reInitated
                             State.st.updateStorageSize(message.getChunk().length)) {
                         try {
                             DigestFile.writeChunk(message.getFileId(), message.getChunkNo(),
                                     message.getChunk(), message.getChunk().length);
                         } catch (IOException e) {
                             e.printStackTrace();
                             State.st.updateStorageSize(-message.getChunk().length);
                         }

                         // Add sequence number
                         chunkId = DigestFile.getId(message.getChunk());
                         State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), chunkId, message.getSeqNumber());
                         iStoredTheChunk = true;
                     }
                 } else {
                     if (message.reInitiateBackup()) reInitBackup = true;
                     // Update sequence number
                     chunkId = DigestFile.getId(message.getChunk());
                     State.st.setAmStoringChunk(message.getFileId(), message.getChunkNo(), message.getSeqNumber());
                     iStoredTheChunk = true;
                 }
             } else { // We are the initator
                filePath = State.st.getFileInfo(message.getFileId()).getFilePath();
                if (message.reInitiateBackup()) reInitBackup = true;
            }
        }

        if (reInitBackup) { // This happens when the chunk is missing from the msg, so we fill it
            byte[] chunk;
            int rep = State.st.getFileDeg(message.getFileId());
            try {
                if (!isInitiator)
                    chunk = DigestFile.readChunk(message.getFileId(), message.getChunkNo());
                else
                    chunk = DigestFile.divideFileChunk(filePath, message.getChunkNo());
            } catch (IOException e) {
                System.err.println("No chunk :" + message.getFileId() + " " + message.getChunkNo() + " found");
                return;
            }
            message.setChunk(chunk);
            message.setReplication(rep);
            message.setSeqNumber(rep);
        }

        // I am responsible and i stored the message
        if (iStoredTheChunk && message.getSeqNumber() == message.getReplication()) {
            System.out.println("I am responsible for chunk " + message.getFileId() + " " + message.getChunkNo());
            message.setSource(this.chordNode);
        }

        // send STORED reply message to our predecessor if we stored the chunk/already had it
        if (iStoredTheChunk) {
            StoredMsg response = new StoredMsg(message.getFileId(), this.address, this.port,
                    message.getChunkNo(), chunkId);
            InetAddress address; int port;
            try {
                address = this.chordNode.getPredecessor().getAddress();
                port = this.chordNode.getPredecessor().getPort();
            } catch (RemoteException e) {
                System.err.println("Couldn't find predecessor to send him a STORED reply");
                return;
            }
            this.chordNode.sendDirectly(response, address, port);
            message.decreaseCurrentRep(); // Update current rep in putchunk chain
        }

        // Propagate putchunks through successors
        if (message.getSeqNumber() == 0) // We don't need to resend the putchunk message further, we are last in the chain
            return;

        this.chordNode.sendToSucc(message);
    }

    private void handleMsg(StoredMsg message) {
        synchronized (State.st) {
            State.st.addSuccChunk(message.getFileId(), message.getChunkNo(), message.getChunkId());
        }
    }

    private void handleMsg(ChunkMsg message) {
        Pair<String, Integer> msgChunk = new Pair<>(message.getFileId(), message.getChunkNo());
        if (this.receivedChunks.containsKey(msgChunk))
            this.receivedChunks.get(msgChunk).complete(message.getChunk());
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

        try {
            this.chordNode.sendDirectly(message, chordNode.getSuccessor());
        } catch (RemoteException e) {
            System.err.println("Couldn't pass " + message + " to my successor");
        }
    }

    private void handleMsg(GetChunkMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            // Mark getchunk has unsuccessful
            var filePair = new Pair<>(message.getFileId(), message.getChunkNo());
            if (this.receivedChunks.containsKey(filePair))
                this.receivedChunks.get(filePair).complete(null);
            return; // We sent this message and it has looped through the network
        }

        synchronized (State.st) {
            if (!State.st.amIStoringChunk(message.getFileId(), message.getChunkNo())) {
                // Resend to next node in ring
                try {
                    this.chordNode.sendDirectly(message, chordNode.getSuccessor());
                } catch (RemoteException e) {
                    System.err.println("Couldn't pass " + message + " to my successor");
                    return;
                }

                return;
            }
        }

        ChunkMsg response = new ChunkMsg(message.getFileId(), message.getChunkNo(), this.address, this.port, null);
        response.setSource(this.chordNode);
        this.chordNode.sendDirectly(response, message.getSourceAddress(), message.getSourcePort());
    }

    private void handleMsg(RemovedMsg message) {
        if (this.messageSentByUs(message) && message.destAddrKnown()) {
            System.out.println("\t\tMessage looped through network " + message);
            return; // We sent this message and it has looped through the network
        }

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
                    System.out.println("couldn't read supposed store chunk " + message.getFileId() + " " + message.getChunkNo());
                    return;
                }
            }
        }

        if (isInitiator || isStoringChunk) { // If we have the file that got deleted
            // We don't give the source so that the responsible node for this chunk fills it when it receives the msg
            this.chordNode.send(new PutChunkMsg(message.getFileId(), message.getChunkNo(), c, replication, DigestFile.getId(c)));
        } else {
            // Resend to next node in ring
            try {
                this.chordNode.sendDirectly(message, chordNode.getSuccessor());
            } catch (RemoteException e) {
                System.err.println("Couldn't pass " + message + " to my successor");
                return;
            }

            // We don't need the chord ring to hop to the dest, we just need to hop it to next successor successively
            message.setDestId(null);
        }
    }

    private boolean messageSentByUs(Message message) {
        return (message.getSourcePort() == this.chordNode.getPort() &&
                message.getSourceAddress().equals(this.chordNode.getAddress()) &&
                message.destAddrKnown());
    }

    public void handleMessage(Message message) {
//        TODO i don't think we want this here, we want some message to loop through the ring. Handled by each message differently
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
