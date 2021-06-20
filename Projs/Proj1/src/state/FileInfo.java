package state;

import utils.Pair;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FileInfo implements Serializable {
    // chunkNo -> [Peers que deram store] + Se eu estou a dar store
    private final ConcurrentMap<Integer, Pair<HashSet<String>, Boolean>> chunkInfo;
    private String filePath = null;  // only set if we are the initiator
    private Integer desiredRep;

    public FileInfo(int desiredRep) {
        this.desiredRep = desiredRep;
        this.chunkInfo = new ConcurrentHashMap<>();
    }

    public FileInfo(String filePath, int desiredRep) {
        this(desiredRep);
        this.filePath = filePath;
    }

    public void declareChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo))
            this.chunkInfo.put(chunkNo, new Pair<>(new HashSet<>(), false));
    }

    public boolean amIStoringChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo)) return false;
        return this.chunkInfo.get(chunkNo).p2;
    }

    public void setAmStoringChunk(int chunkNo, boolean amStoring) {
        if (!this.chunkInfo.containsKey(chunkNo)) return;
        this.chunkInfo.get(chunkNo).p2 = amStoring;
    }

    // initiator
    public boolean isInitiator() {
        return this.filePath != null;
    }

    public String getFilePath() {
        return this.filePath;
    }

    // replication degree
    public int getDesiredRep() {
        return desiredRep;
    }

    public void setDesiredRep(int desiredRep) {
        this.desiredRep = desiredRep;
    }

    public int getChunkPerceivedRep(int chunkNo) {
        return this.chunkInfo.get(chunkNo).p1.size();
    }

    public HashSet<String> getPeersStoringChunk(int chunkNo) {
        return this.chunkInfo.get(chunkNo).p1;
    }

    public Set<String> getPeersStoringFile() {
        Set<String> res = new HashSet<>();
        for (var chunkInfo : this.chunkInfo.entrySet()) {
            res.addAll(chunkInfo.getValue().p1);
        }
        return res;
    }

    public void incrementChunkDeg(int chunkNo, String peerId) {
        if (this.chunkInfo.containsKey(chunkNo)) {
            Pair<HashSet<String>, Boolean> chunk = this.chunkInfo.get(chunkNo);
            chunk.p1.add(peerId);
        } else {
            HashSet<String> s = new HashSet<>() {{
                add(peerId);
            }};
            this.chunkInfo.put(chunkNo, new Pair<>(s, false));
        }
    }

    public void decrementChunkDeg(int chunkNo, String peerId) {
        if (this.chunkInfo.containsKey(chunkNo)) {
            Pair<HashSet<String>, Boolean> chunk = this.chunkInfo.get(chunkNo);
            chunk.p1.remove(peerId);
        } else {
            this.chunkInfo.put(chunkNo, new Pair<>(new HashSet<>(), false));
        }
    }

    // iteration
    public Map<Integer, Pair<HashSet<String>, Boolean>> getAllChunks() {
        return this.chunkInfo;
    }

}
