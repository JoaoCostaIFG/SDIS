package state;

import file.DigestFile;
import utils.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// this class is a singleton
public class State implements Serializable {
    public transient static final String REPMAPNAME = "repMap.txt";
    public transient static final State st = State.importMap();

    // stores the running tasks for recovering on program end
    private final ConcurrentHashMap<String[], Boolean> tasks;

    // fileId -> fileInformation
    private final ConcurrentMap<String, FileInfo> replicationMap;
    // stores the chunks that our successor is storing (FileId, ChunkId) -> ChunkNo
    private Map<Pair<String, Integer>, Integer> succChunks;

    private volatile Long maxDiskSpaceB;
    private volatile transient long filledStorageSizeB;

    private State() {
        this.tasks = new ConcurrentHashMap<>();
        this.replicationMap = new ConcurrentHashMap<>();
        this.maxDiskSpaceB = -1L;
        succChunks = new HashMap<>();
    }

    public static State getState() {
        return st;
    }

    // FOR SERIALIZATION
    public static State importMap() {
        State ret;
        try {
            FileInputStream fileIn = new FileInputStream(DigestFile.PEER_DIR + REPMAPNAME);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            ret = (State) in.readObject();
            in.close();
            fileIn.close();
        } catch (IOException | ClassNotFoundException e) {
            ret = new State();
        }

        ret.initFilledStorage();

        return ret;
    }

    public static void exportMap() throws IOException {
        try {
            FileOutputStream fileOut = new FileOutputStream(DigestFile.PEER_DIR + REPMAPNAME);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(State.st);
            out.close();
            fileOut.close();
        } catch (IOException e) {
            System.err.println("Program state saving to non-volatile storage failed.");
        }
    }

    // RUNNING TASKS
    public void addTask(String[] task) {
        this.tasks.put(task, false);
    }

    public void rmTask(String[] task) {
        this.tasks.remove(task);
    }

    public List<String[]> getTasks() {
        List<String[]> ret = new ArrayList<>(this.tasks.keySet());
        this.tasks.clear();
        return ret;
    }

    // STORAGE
    public Long getMaxDiskSpaceB() {
        return maxDiskSpaceB;
    }

    public void setMaxDiskSpaceB(Long maxDiskSpaceB) {
        this.maxDiskSpaceB = maxDiskSpaceB;
    }

    public Long getMaxDiskSpaceKB() {
        return maxDiskSpaceB < 0 ? -1 : maxDiskSpaceB / 1000;
    }

    public void initFilledStorage() {
        this.filledStorageSizeB = DigestFile.getStorageSize();
    }

    public long getFilledStorageB() {
        return this.filledStorageSizeB;
    }

    public boolean updateStorageSize(long sizeToAddB) {
        if (sizeToAddB < 0) {
            filledStorageSizeB += sizeToAddB;
            return true;
        }

        if (maxDiskSpaceB < 0) { // is infinite
            filledStorageSizeB += sizeToAddB;
            return true;
        }

        if (filledStorageSizeB + sizeToAddB < maxDiskSpaceB) {
            filledStorageSizeB += sizeToAddB;
            return true;
        }
        return false;
    }

    public boolean isStorageFull() {
        return this.maxDiskSpaceB >= 0 && (this.filledStorageSizeB >= this.maxDiskSpaceB);
    }

    public boolean isInitiator(String fileId) {
        if (!this.replicationMap.containsKey(fileId)) return false;
        return this.replicationMap.get(fileId).isInitiator();
    }

    public String getHashByFileName(String filename) {
        for (String fileId : this.replicationMap.keySet()) {
            FileInfo fileInfo = this.replicationMap.get(fileId);
            if (fileInfo.isInitiator()) {
                if (fileInfo.getFilePath().equals(filename))
                    return fileId;
            }
        }
        return null;
    }

    // ADD
    public void addFileEntry(String fileId, String filePath, int desiredRep) {
        if (!this.replicationMap.containsKey(fileId)) {
            this.replicationMap.put(fileId, new FileInfo(filePath, desiredRep));
        } else {
            FileInfo fileInfo = this.replicationMap.get(fileId);
            fileInfo.setDesiredRep(desiredRep);
        }


    }

    public void addFileEntry(String fileId, int desiredRep) {
        if (!this.replicationMap.containsKey(fileId)) {
            this.replicationMap.put(fileId, new FileInfo(desiredRep));
        } else {
            FileInfo fileInfo = this.replicationMap.get(fileId);
            fileInfo.setDesiredRep(desiredRep);
        }
    }

    public void removeFileEntry(String fileId) {
        this.replicationMap.remove(fileId);
    }

    public void declareChunk(String fileId, int chunkNo) {
        // only declares if it isn't declared yet
        if (!this.replicationMap.containsKey(fileId)) return;
        this.replicationMap.get(fileId).declareChunk(chunkNo);
    }

    // REPLICATION DEGREE
    public int getFileDeg(String fileId) {
        // file desired rep
        if (!this.replicationMap.containsKey(fileId)) return 0;

        return this.replicationMap.get(fileId).getDesiredRep();
    }

    // SUCCESSOR STORED CHUNKS
    public void addSuccChunk(String fileId, int chunkNo, int chunkId) {
        this.succChunks.put(new Pair<>(fileId, chunkNo), chunkId);
    }

    public void removeSuccChunk(String fileId, int chunkNo) {
        this.succChunks.remove(new Pair<>(fileId, chunkNo));
    }

    public void removeSuccChunk(String fileId) {
        this.succChunks.entrySet().removeIf(e -> e.getKey().p1.equals(fileId));
    }

    public void replaceSuccChunk(Map<Pair<String, Integer>, Integer> map) {
        this.succChunks = map;
    }

    public void clearSuccChunks() {
        this.succChunks.clear();
    }

    public boolean hasSuccChunks() {
        return this.succChunks.size() != 0;
    }

    public Map<Pair<String, Integer>, Integer> getSuccChunksIds() {
        return this.succChunks;
    }

    // OTHER
    public boolean amIStoringChunk(String fileId, int chunkNo) {
        if (!this.replicationMap.containsKey(fileId)) return false;
        return this.replicationMap.get(fileId).amIStoringChunk(chunkNo);
    }

    public void setAmStoringChunk(String fileId, int chunkNo, int chunkId, int seqNumber) {
        if (!this.replicationMap.containsKey(fileId)) return;
        this.replicationMap.get(fileId).setAmStoringChunk(chunkNo, chunkId, seqNumber);
    }

    public void setAmStoringChunk(String fileId, int chunkNo, int seqNumber) {
        if (!this.replicationMap.containsKey(fileId)) return;
        this.replicationMap.get(fileId).setAmStoringChunk(chunkNo, seqNumber);
    }

    // ITERATION
    public FileInfo getFileInfo(String fileId) {
        if (!this.replicationMap.containsKey(fileId)) return null;
        return this.replicationMap.get(fileId);
    }

    public Map<String, FileInfo> getAllFilesInfo() {
        return this.replicationMap;
    }

    public Map<Pair<String, Integer>, Integer> getAllStoredChunksId() {
        Map<Pair<String, Integer>, Integer> res = new HashMap<>();
        for (String fileId : this.replicationMap.keySet()) {
            FileInfo fileInfo = this.replicationMap.get(fileId);
            if (!fileInfo.isInitiator()) {
                for (var chunk: fileInfo.getAllChunks().entrySet()) {
                    int chunkNo = chunk.getKey();
                    if (fileInfo.amIStoringChunk(chunkNo)) {
                        int chunkId = chunk.getValue().p1;
                        res.put(new Pair<>(fileId, chunkNo), chunkId);
                    }
                }
            }
        }
        return res;
    }
}
