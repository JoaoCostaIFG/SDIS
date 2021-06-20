import chord.ChordController;
import chord.ChordInterface;
import file.DigestFile;
import message.DeleteMsg;
import message.GetChunkMsg;
import message.PutChunkMsg;
import message.RemovedMsg;
import state.FileInfo;
import state.State;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Peer implements TestInterface {
    private ChordController chordController;
    private boolean closed = false;
    // cmd line arguments
    private final String id;
    private final InetAddress address;
    private final int port;

    public Registry registry = null;

    public Peer(String[] args) throws IOException {
        // parse args
        if (args.length != 3) usage();

        this.id = args[0];
        // set the file dir name for the rest of the program (create it if missing)
        // and get info
        DigestFile.setFileDir(this.id);

        this.address = InetAddress.getByName(args[1]);
        this.port = Integer.parseInt(args[2]);

        System.out.println(this);
        System.out.println("Initialized program.");
    }

    private void initCoordNode() throws IOException {
        this.chordController = new ChordController(address, port, registry);
        ChordController chordNode = this.chordController;

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            chordNode.getChordNode().stabilize();
                        } catch (RemoteException ignored) {
                        }
                    }
                },
                0,
                100
        );

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            chordNode.getChordNode().stabilize();
                        } catch (Exception ignored) {
                        }
                    }
                },
                23,
                100
        );

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            chordNode.getChordNode().checkPredecessor();
                        } catch (Exception ignored) {
                        }
                    }
                },
                83,
                100
        );
    }

    private void handlePendingTasks() {
        List<String[]> pendingTasks = State.st.getTasks();
        for (String[] task : pendingTasks) {
            if (task.length < 1) continue;
            switch (task[0]) {
                case "BACKUP":
                    if (task.length != 3) continue;
                    try {
                        this.backup(task[1], Integer.parseInt(task[2]));
                    } catch (RemoteException e) {
                        System.err.println("Failed to redo pending task: BACKUP " + task[1] + " " + task[2]);
                    }
                    break;
                case "RESTORE":
                    if (task.length != 2) continue;
                    try {
                        this.restore(task[1]);
                    } catch (RemoteException e) {
                        System.err.println("Failed to redo pending task: RESTORE " + task[1]);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void verifyModifiedFiles() {
        String errorMsg;
        Map<String, FileInfo> fileMap = State.st.getAllFilesInfo();
        for (String oldFileId : fileMap.keySet()) {
            FileInfo fileInfo = fileMap.get(oldFileId);
            if (fileInfo.isInitiator()) {
                String filePath = fileInfo.getFilePath();
                String newFileId;
                try {
                    newFileId = DigestFile.getHash(filePath);
                } catch (IOException e) {
                    newFileId = ""; // File not present, delete it
                }
                if (newFileId.equals("")) { // File not present, delete it
                    errorMsg = this.deleteFromId(oldFileId);
                    if (!errorMsg.equals("Success")) {
                        System.err.println(errorMsg + "(Peer Init)");
                        return;
                    }
                } else {
                    if (!newFileId.equals(oldFileId)) { // If the file changed when we were Zzz
                        try {
                            errorMsg = this.deleteFromId(oldFileId.strip());
                            if (!errorMsg.equals("Success")) {
                                System.err.println(errorMsg + "(Peer Init)");
                                return;
                            }
                            this.backup(filePath, fileInfo.getDesiredRep());
                        } catch (RemoteException e) {
                            System.err.println("Fail when deleting file (Peer Init)" + filePath);
                        }
                    }
                }
            }
        }
    }

    public void cleanup() {
        if (closed) return;
        closed = true;

        // cleanup the access point
        if (registry != null) {
            try {
                if (this.chordController != null) // Chord node might not be initiated yet
                    registry.unbind(String.valueOf(chordController.getId()));
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Failed to unregister our chordNode from the RMI service.");
            }
            try {
                assert chordController != null;
                registry.unbind("peer" + this.id);
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Failed to unregister our Peer instance from the RMI service.");
            }
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (Exception e) {
                System.err.println("Failed to unexport our RMI service.");
            }
        }

        try {
            State.exportMap();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mainLoop() {
        this.chordController.start();
        Scanner scanner = new Scanner(System.in);
        String cmd;
        do {
            cmd = scanner.nextLine();
            System.out.println("CMD: " + cmd);
            String filePath = "../test_files/big64k.txt";
            // String filePath = "../test_files/test.txt";
            // String filePath = "../test_files/filename.txt";
            // String filePath = "../test_files/64k.txt";
            if (cmd.equalsIgnoreCase("join")) {
                try {
                    System.out.println(this.join());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("st")) {
                System.out.println(this.chordController);
            } else if (cmd.startsWith("backup")) {
                String[] opts = cmd.split(" ");
                if (opts.length != 2) {
                    System.out.println("Usage: backup repdegree");
                    continue;
                }
                try {
                    this.backup(filePath, Integer.parseInt(opts[1]));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.startsWith("reclaim")) {
                String[] opts = cmd.split(" ");
                if (opts.length != 2) {
                    System.out.println("Usage: reclaim size");
                    continue;
                }
                try {
                    this.reclaim(Integer.parseInt(opts[1]));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("restore")) {
                try {
                    System.out.println(this.restore(filePath));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("delete")) {
                try {
                    System.out.println(this.delete(filePath));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("state")) {
                try {
                    System.out.println(this.state());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } while (!cmd.equalsIgnoreCase("EXIT"));

        // shush threads
        this.chordController.stop();
    }

    /* used by the TestApp (RMI) */
    @Override
    public String join() throws RemoteException {
        String[] l = this.registry.list();
        String peerId = null;
        for (var e : l) {
            if (!e.startsWith("peer") && !e.equals(String.valueOf(chordController.getId()))) {
                peerId = e;
            }
        }
        if (peerId == null)
            return "Couldn't find a node to join the network with";

        ChordInterface node;
        try {
            node = (ChordInterface) this.registry.lookup(peerId);
        } catch (NotBoundException e) {
            return "An attempted lookup to a node in the network failed";
        }
        this.chordController.join(node);
        System.out.println("Joined peer with id " + peerId);
        // Resume pending tasks
        this.handlePendingTasks();
        return "Join success";
    }

    @Override
    public String backup(String filePath, Integer replicationDegree) throws RemoteException {
        String[] task = new String[]{"BACKUP", filePath, replicationDegree.toString()};
        State.st.addTask(task);

        String fileId;
        List<byte[]> chunks;
        try {
            fileId = DigestFile.getHash(filePath);

            // Check if file with same path and with different hash was already stored for backup
            // doesn't need synchronized because it only uses concurrent objs
            String oldFileId = State.st.getHashByFileName(filePath);
            if (oldFileId != null) {
                if (!oldFileId.equals(fileId)) { // Files were different, delete old file
                    String errorMsg = this.deleteFromId(oldFileId);
                    if (!errorMsg.equals("Success"))
                        return "Failed to delete old version of file: " + errorMsg;
                }
            }

            chunks = DigestFile.divideFile(filePath, replicationDegree);
        } catch (IOException e) {
            State.st.rmTask(task);
            throw new RemoteException("Couldn't divide file " + filePath);
        }

        for (int i = 0; i < chunks.size(); ++i) {
            int destId = DigestFile.getId(fileId, i);
            this.chordController.send(new PutChunkMsg(fileId, i, chunks.get(i), replicationDegree, destId));
        }

        State.st.rmTask(task);

        return "Backed up the file: " + filePath;
    }

    @Override
    public String restore(String filePath) throws RemoteException {
        String[] task = new String[]{"RESTORE", filePath};
        State.st.addTask(task);

        String fileId;
        int chunkNo;

        try {
            fileId = DigestFile.getHash(filePath);
            chunkNo = DigestFile.getChunkCount(filePath); // TODO: Esperar até o ultimo ter size 0?
        } catch (Exception e) {
            State.st.rmTask(task);
            throw new RemoteException("Failed to restore the file " + filePath);
        }

        if (chunkNo < 0) {
            State.st.rmTask(task);
            throw new RemoteException("File " + filePath + " is too big");
        }

        // Storing the futures to be able to restore the file after getting all the chunks (or failing
        // if a chunk is missing)
        List<CompletableFuture<byte[]>> promisedChunks = new ArrayList<>();
        for (int currChunk = 0; currChunk < chunkNo; ++currChunk) {
            // Add future to node so that it notifies it
            CompletableFuture<byte[]> fut = new CompletableFuture<>();
            promisedChunks.add(fut);
            this.chordController.addChunkFuture(fileId, currChunk, fut);

            // Send getchunk message
            int destId = DigestFile.getId(fileId, currChunk);
            this.chordController.send(new GetChunkMsg(fileId, currChunk, this.address, this.port, destId));
        }

        List<byte[]> chunks = new ArrayList<>(chunkNo);
        for (int currChunk = 0; currChunk < chunkNo; ++currChunk) {
            CompletableFuture<byte[]> fut = promisedChunks.get(currChunk);
            try {
                byte[] chunk = fut.get();
                if (chunk == null) { // Getchunk passed through everyone and didn't work
                    this.chordController.removeAllChunkFuture(fileId); // clean up all promises (we won't need them)
                    State.st.rmTask(task);
                    throw new RemoteException("Couldn't get chunk " + currChunk);
                }
                chunks.add(chunk);
            } catch (InterruptedException | ExecutionException e) {
                State.st.rmTask(task);
                throw new RemoteException("Couldn't get chunk " + currChunk);
            }
        }

        Path path = Paths.get(filePath);
        try {
            DigestFile.assembleFile(path.getFileName().toString(), chunks);
        } catch (IOException e) {
            State.st.rmTask(task);
            throw new RemoteException("Failed to write restored file: " + path.getFileName().toString());
        }

        State.st.rmTask(task);
        return "Restored file " + filePath + " with hash " + fileId + ".";
    }

    public String deleteFromId(String fileId) {
        // we don't want the old entry anymore
        State.st.removeFileEntry(fileId);
        this.chordController.send(new DeleteMsg(fileId, this.chordController.getAddress(), this.chordController.getPort(), this.chordController.getId()));
        return "Success";
    }

    @Override
    public String delete(String filePath) throws RemoteException {
        String fileId;
        try {
            fileId = DigestFile.getHash(filePath);
        } catch (IOException e) {
            throw new RemoteException("Deletion of " + filePath + " failed.");
        }
        return "File " + filePath + " deletion: " + this.deleteFromId(fileId);
    }

    // force == true => ignore if the the replication degree becomes 0
    // returns capacity left to trim
    private long trimFiles(long capactityToTrim) throws RemoteException {
        if (capactityToTrim <= 0) return 0;

        long currentCap = capactityToTrim;

        for (var entry : State.st.getAllFilesInfo().entrySet()) {
            String fileId = entry.getKey();
            for (var chunkEntry : entry.getValue().getAllChunks().entrySet()) {
                int chunkNo = chunkEntry.getKey();
                boolean isStored = chunkEntry.getValue().p2 != -1;

                if (isStored) {
                    // if we have the chunk stored => delete it && decrement perceived rep.
                    int chunkId = DigestFile.getId(fileId, chunkNo);
                    // Delete the chunk and update state
                    long chunkSize = DigestFile.deleteChunk(fileId, chunkNo); // updates state capacity
                    State.st.setAmStoringChunk(fileId, chunkNo, -1);
                    currentCap -= chunkSize;
                    // Send Removed message to the responsible of the chunk
                    this.chordController.send(new RemovedMsg(fileId, chunkNo, chunkId, chunkId, false));

                    // Send Removed message to our predecessor so that it updates the chunks that it thinks we store
                    RemovedMsg predMsg = new RemovedMsg(fileId, chunkNo, chunkId,
                            chunkId, true);
                    this.chordController.sendToPred(predMsg);
                }

                if (currentCap <= 0)
                    break;
            }

            if (currentCap <= 0)
                break;
        }

        return currentCap;
    }

    @Override
    public String reclaim(int newMaxDiskSpaceKB) throws RemoteException {
        long newMaxDiskSpaceB = newMaxDiskSpaceKB * 1000L;
        boolean isDone = false;

        synchronized (State.st) {
            if (newMaxDiskSpaceB < 0) {
                State.st.setMaxDiskSpaceB(-1L);
                // infinite capacity => do nothing
                isDone = true;
            } else if (State.st.getMaxDiskSpaceB() >= 0) {
                long capacityDelta = newMaxDiskSpaceB - State.st.getMaxDiskSpaceB();
                State.st.setMaxDiskSpaceB(newMaxDiskSpaceB);
                // if max capacity is unchanged or increases, we don't need to do anything
                if (capacityDelta >= 0)
                    isDone = true;
            } else {
                State.st.setMaxDiskSpaceB(newMaxDiskSpaceB);
            }

            if (!isDone) {
                long currentCap = State.st.getFilledStorageB() - State.st.getMaxDiskSpaceB();
                if (currentCap > 0) {
                    // remove things (trying to keep everything above 0 replication degree)
                    System.err.println("Freeing: " + currentCap);
                    trimFiles(currentCap);
                }
            }
        }

        return "Max disk space set to " + (newMaxDiskSpaceKB < 0 ? "infinite" : newMaxDiskSpaceKB) + " KBytes.";
    }

    @Override
    public String state() throws RemoteException {
        StringBuilder filesIInitiated = new StringBuilder();
        filesIInitiated.append("Files I initiated the backup of:\n");
        StringBuilder chunksIStore = new StringBuilder();
        chunksIStore.append("Chunks I am storing:\n");
        StringBuilder chunksSuccIsStoring = new StringBuilder();
        chunksSuccIsStoring.append("Chunks my succ is storing:\n");

        long maxStorageSizeKB;
        long filledB;

        synchronized (State.st) {
            for (var entry : State.st.getAllFilesInfo().entrySet()) {
                String fileId = entry.getKey();
                FileInfo fileInfo = entry.getValue();

                if (fileInfo.isInitiator()) {
                    filesIInitiated.append("\tFile path: ").append(fileInfo.getFilePath()).append("\n");
                    filesIInitiated.append("\t\tFile ID: ").append(fileId).append("\n");
                    filesIInitiated.append("\t\tDesired replication degree: ").append(fileInfo.getDesiredRep()).append("\n");
                    filesIInitiated.append("\t\tChunks:\n");
                    for (var chunkEntry : fileInfo.getAllChunks().entrySet()) {
                        filesIInitiated.append("\t\t\tID: ").append(chunkEntry.getKey()).append("\n");
                    }
                } else {
                    chunksIStore.append("\tFile ID: ").append(fileId).append("\n");
                    for (var chunkEntry : fileInfo.getAllChunks().entrySet()) {
                        int chunkNo = chunkEntry.getKey();
                        int chunkId = fileInfo.getChunkId(chunkNo);
                        boolean isStored = chunkEntry.getValue().p2 != -1;
                        if (!isStored)  // only show chunks we are currently storing
                            continue;

                        chunksIStore.append("\tChunk no: ").append(chunkNo).append(" Id: ").append(chunkId).append("\n");
                        chunksIStore.append("\t\tSize: ").append(DigestFile.getChunkSize(fileId, chunkNo)).append("\n");
                        chunksIStore.append("\t\tDesired replication degree: ").append(fileInfo.getDesiredRep()).append("\n");
                    }
                }
            }

            for (var entry2 : State.st.getSuccChunksIds().entrySet())
                chunksSuccIsStoring.append("\tFileId: ").append(entry2.getKey().p1)
                        .append(" ChunkNo: ").append(entry2.getKey().p2)
                        .append(" ChunkId: ").append(entry2.getValue()).append("\n");

            maxStorageSizeKB = State.st.getMaxDiskSpaceKB();
            filledB = State.st.getFilledStorageB();
        }

        long filledKB = Math.round(filledB / 1000.0);
        return filesIInitiated
                .append(chunksIStore)
                .append(chunksSuccIsStoring)
                .append("Storing ").append(filledKB == 0 ? (filledB + "B") : (filledKB + "KB"))
                .append(" of a maximum of ")
                .append(maxStorageSizeKB < 0 ? "infinite " : maxStorageSizeKB).append("KB.")
                .toString();
    }

    @Override
    public String toString() {
        return "Peer id (and access point): peer" + this.id + "\n";
    }

    private static void usage() {
        System.err.println("Usage: java\n" +
                "\tPeer <peer id>\n" +
                "\t<ip address> <port>");
        System.exit(1);
    }

    public static void main(String[] args) {
        // parse cmdline args
        Peer prog = null;

        try {
            prog = new Peer(args);
        } catch (IOException e) {
            System.err.println("Couldn't initialize the program.");
            e.printStackTrace();
            usage();
        }

        assert prog != null;

        // trap sigint
        Peer finalProg = prog;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Exiting gracefully..");
            finalProg.cleanup();
        }));

        // In this function we are verifying the modified files :). Hope your day is going as intended. Bye <3
        prog.verifyModifiedFiles();

        // setup the access point
        TestInterface stub;
        try {
            stub = (TestInterface) UnicastRemoteObject.exportObject(prog, 0);
            prog.registry = LocateRegistry.getRegistry();
            prog.registry.bind(("peer" + prog.id), stub);
        } catch (Exception e) {
            System.err.println("Failed setting up the access point for use by the testing app.");
            System.exit(1);
            // e.printStackTrace();
        }
        try {
            prog.initCoordNode();
        } catch (IOException e) {
            System.err.println("Couldn't create node socket");
        }

        prog.mainLoop();
        System.exit(0);
    }
}
