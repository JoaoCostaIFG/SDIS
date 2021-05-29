package state;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FileInfo implements Serializable {
    // chunkNo -> Num de sequencia (-1 Se nao estou a dar store)
    private final ConcurrentMap<Integer, Integer> chunkInfo;
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
            this.chunkInfo.put(chunkNo, -1);
    }

    public boolean amIStoringChunk(int chunkNo) {
        if (!this.chunkInfo.containsKey(chunkNo)) return false;
        return this.chunkInfo.get(chunkNo) != -1;
    }

    public void setAmStoringChunk(int chunkNo, int seqNumber) {
        if (!this.chunkInfo.containsKey(chunkNo)) return;
        this.chunkInfo.put(chunkNo, seqNumber);
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
    public Map<Integer, Integer> getAllChunks() {
        return this.chunkInfo;
    }

}
