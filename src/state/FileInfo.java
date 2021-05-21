package state;

import utils.Pair;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FileInfo implements Serializable {
    // chunkNo -> Se eu estou a dar store
    private final ConcurrentMap<Integer, Boolean> chunkInfo;
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
            this.chunkInfo.put(chunkNo, false);
    }

    public boolean amIStoringChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo)) return false;
        return this.chunkInfo.get(chunkNo);
    }

    public void setAmStoringChunk(int chunkNo, boolean amStoring) {
        if (!this.chunkInfo.containsKey(chunkNo)) return;
        this.chunkInfo.put(chunkNo, amStoring);
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


    // iteration
    public Map<Integer, Boolean> getAllChunks() {
        return this.chunkInfo;
    }

}
