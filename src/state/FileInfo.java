package state;

import utils.Pair;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FileInfo implements Serializable {
    // chunkNo -> (Id do chunk, Num de sequencia (-1 Se nao estou a dar store))
    private final ConcurrentMap<Integer, Pair<Integer, Integer>> chunkInfo;
    private String filePath = null;  // only set if we are the initiator
    private Integer desiredRep;

    public FileInfo(int desiredRep) {
        this.desiredRep = desiredRep;
        this.chunkInfo = new ConcurrentHashMap<Integer, Pair<Integer, Integer>>();
    }

    public FileInfo(String filePath, int desiredRep) {
        this(desiredRep);
        this.filePath = filePath;
    }

    public void declareChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo))
            this.chunkInfo.put(chunkNo, new Pair<Integer, Integer>(-1, -1));
    }

    public boolean amIStoringChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo)) return false;
        return this.chunkInfo.get(chunkNo).p2 != -1;
    }

    public void setAmStoringChunk(int chunkNo, int chunkId, int seqNumber) {
        if (!this.chunkInfo.containsKey(chunkNo)) return;
        this.chunkInfo.put(chunkNo, new Pair<>(chunkId, seqNumber));
    }

    public void setAmStoringChunk(int chunkNo, int seqNumber) {
        if (!this.chunkInfo.containsKey(chunkNo)) return;
        setAmStoringChunk(chunkNo, this.chunkInfo.get(chunkNo).p1, seqNumber);
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

    public int getSeqNumber(int chunkNo) {
        return this.chunkInfo.get(chunkNo).p2;
    }

    public Integer getChunkId(int chunkNo) {
        return this.chunkInfo.get(chunkNo).p1;
    }



    // iteration
    public ConcurrentMap<Integer, Pair<Integer, Integer>> getAllChunks() {
        return  this.chunkInfo;
    }

}
