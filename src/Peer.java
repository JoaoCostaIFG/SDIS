import chord.ChordInterface;
import chord.ChordNode;
import file.DigestFile;
import message.GetChunkMsg;
import message.PutChunkMsg;
import state.FileInfo;
import state.State;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Peer implements TestInterface {
    private ChordNode chordNode;
    private boolean closed = false;
    // cmd line arguments
    private final String id;
    private final String accessPoint;
    private final InetAddress address;
    private final int port;


    // thread pool
    private final ScheduledExecutorService testAppThreadPool =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1);

    public Registry registry = null;
    public String rmiName = null;

    public String getAccessPointName() {
        return this.accessPoint;
    }

    public Peer(String[] args) throws IOException {
        // parse args
        if (args.length != 4) usage();

        this.id = args[0];
        // set the file dir name for the rest of the program (create it if missing)
        // and get info
        DigestFile.setFileDir(this.id);

        this.accessPoint = args[1];
        this.address = InetAddress.getByName(args[2]);
        this.port = Integer.parseInt(args[3]);

        System.out.println(this);
        System.out.println("Initialized program.");
    }

    private void initCoordNode() throws IOException {
        this.chordNode = new ChordNode(address, port, registry);
        ChordNode chordNode = this.chordNode;

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        try {
                            chordNode.stabilize();
                        } catch (RemoteException e) {
                        }
                    }
                },
                1000,
                1000
        );

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        chordNode.fixFingers();
                    }
                },
                1000,
                1000
        );

        new java.util.Timer().scheduleAtFixedRate(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        chordNode.checkPredecessor();
                    }
                },
                10000,
                10000
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

        // shutdown executors
        this.testAppThreadPool.shutdownNow();

        // cleanup the access point
        if (registry != null) {
            try {
                if (this.chordNode != null) // Chord node might not be initiated yet
                    registry.unbind(String.valueOf(chordNode.getId()));
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Failed to unregister our chordNode from the RMI service.");
            }
            try {
                registry.unbind(rmiName);
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
        this.chordNode.start();
        Scanner scanner = new Scanner(System.in);
        String cmd;
        do {
            cmd = scanner.nextLine();
            System.out.println("CMD: " + cmd);
            String filePath = "../../test_files/1b.txt";
            if (cmd.startsWith("join")) {
                String[] opts = cmd.split(" ");
                if (opts.length != 3) {
                    System.err.println("Join Usage: join addr port");
                    continue;
                }
                String addr = opts[1], port = opts[2];
                InetAddress address;
                try {
                    address = InetAddress.getByName(addr);
                } catch (UnknownHostException e) {
                    System.err.println("Invalid address");
                    continue;
                }
                int nodeId = ChordNode.genId(address, Integer.parseInt(port));
                System.err.println("Gen node id " + nodeId);
                ChordInterface node;
                try {
                    node = (ChordInterface) this.registry.lookup(Integer.toString(nodeId));
                } catch (RemoteException | NotBoundException e) {
                    System.err.println("Failed to find node with given address and port");
                    continue;
                }
                try {
                    this.chordNode.join(node);
                } catch (RemoteException e) {
                    System.err.println("Failed to get response from node");
                    continue;
                }
            } else if (cmd.equalsIgnoreCase("putc")) {
                byte[] c = null;
                String fileId = "";
                try {
                    c = DigestFile.divideFileChunk(filePath, 1);
                    fileId = DigestFile.getHash(filePath);
                } catch (IOException e) {
                    System.err.println(filePath + " not found");
                }

                int destId = DigestFile.getId(c);
                this.chordNode.send(new PutChunkMsg(fileId, 1, c, 4, this.address, this.port, destId));
            } else if (cmd.equalsIgnoreCase("getc")) {
                byte[] c = null;
                String fileId = "";
                try {
                    c = DigestFile.divideFileChunk(filePath, 1);
                    fileId = DigestFile.getHash(filePath);
                } catch (IOException e) {
                    System.err.println(filePath + " not found");
                }

                int destId = DigestFile.getId(c);
                this.chordNode.send(new GetChunkMsg(fileId, 1, this.address, this.port, destId));
            } else if (cmd.startsWith("st")) {
                System.out.println(this.chordNode);
            } else if (cmd.equalsIgnoreCase("backup")) {
                try {
                    this.backup(filePath, 1);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("reclaim")) {
                try {
                    this.reclaim(0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (cmd.equalsIgnoreCase("restore")) {
                try {
                    System.out.println(this.restore(filePath));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } while (!cmd.equalsIgnoreCase("EXIT"));

        // shush threads
        this.chordNode.stop();
    }

    /* used by the TestApp (RMI) */
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
            // only backup chunks that don't have the desired replication degree
//            if (State.st.isChunkOk(fileId, i)) continue;

//            PutChunkMsg putChunkMsg = new PutChunkMsg(this.id,
//                fileId, i, replicationDegree, chunks.get(i));
//            PutChunkSender putChunkSender = new PutChunkSender(this.chordNode, putChunkMsg);
//            putChunkSender.restart();
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
//        List<Pair<Future<?>, MessageSender<? extends Message>>> senders = new ArrayList<>();
//        for (int currChunk = 0; currChunk < chunkNo; ++currChunk) {
//            GetChunkMsg msg = new GetChunkMsg(this.id, fileId, currChunk);
//            MessageSender<? extends Message> chunkSender;
//            chunkSender = new GetChunkSender(this.chordNode, msg);
//            senders.add(new Pair<>(this.testAppThreadPool.submit(chunkSender), chunkSender));
        //}

//        List<byte[]> chunks = new ArrayList<>(chunkNo);
//        for (var sender : senders) {
//            try {
//                sender.p1.get();
//            } catch (InterruptedException | ExecutionException e) {
//                State.st.rmTask(task);
//                throw new RemoteException("There was an error recovering a chunk of the file.");
//            }
//            int chunkNumber;
//            byte[] chunk;
//            GetChunkSender getChunkSender = (GetChunkSender) sender.p2;
//            chunkNumber = getChunkSender.getMessage().getChunkNo();
//
//            if (!sender.p2.getSuccess()) {
//                State.st.rmTask(task);
//                throw new RemoteException("Failed to restore the file " + filePath +
//                        " because of a missing chunk: " + chunkNumber);
//            }
//
//            chunk = getChunkSender.getResponse();
//            chunks.add(chunk);
//        }
//
//        Path path = Paths.get(filePath);
//        try {
//            DigestFile.assembleFile(path.getFileName().toString(), chunks);
//        } catch (IOException e) {
//            State.st.rmTask(task);
//            throw new RemoteException("Failed to write restored file: " + path.getFileName().toString());
//        }
//
//        State.st.rmTask(task);
//        return "Restored file " + filePath + " with hash " + fileId + ".";
        return "TODO";
    }

    public String deleteFromId(String fileId) {
        // we don't want the old entry anymore
        State.st.removeFileEntry(fileId);

//        DeleteMsg msg = new DeleteMsg(this.id, fileId);
//        this.MCSock.send(msg);

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
    private long trimFiles(long capactityToTrim, boolean force) {
        if (capactityToTrim <= 0) return 0;

        long currentCap = capactityToTrim;

        for (var entry : State.st.getAllFilesInfo().entrySet()) {
            String fileId = entry.getKey();
            // int desiredRep = entry.getValue().p1;

            for (var chunkEntry : entry.getValue().getAllChunks().entrySet()) {
                int chunkNo = chunkEntry.getKey();
                boolean isStored = chunkEntry.getValue() == -1;
                if (isStored) {
                    // if we have the chunk stored => delete it && decrement perceived rep.
                    long chunkSize = DigestFile.deleteChunk(fileId, chunkNo); // updates state capacity
//                    State.st.decrementChunkDeg(fileId, chunkNo, this.id);
                    State.st.setAmStoringChunk(fileId, chunkNo, -1);
                    currentCap -= chunkSize;

//                    RemovedMsg removedMsg = new RemovedMsg(this.id, fileId, chunkNo);
                    // TODO Create removedMsgSender
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
    public String reclaim(int newMaxDiskSpaceKB) throws RemoteException { // TODO Adicionar isto aos ENHANCE
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
                    currentCap = trimFiles(currentCap, false);
                    if (currentCap > 0) trimFiles(currentCap, true);

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
                        filesIInitiated.append("\t\t\tID: ").append(chunkEntry.getKey());
                    }
                } else {
                    for (var chunkEntry : fileInfo.getAllChunks().entrySet()) {
                        int chunkId = chunkEntry.getKey();
                        boolean isStored = chunkEntry.getValue() == -1;
                        if (!isStored)  // only show chunks we are currently storing
                            continue;

                        chunksIStore.append("\tChunk ID: ").append(fileId).append(" - ").append(chunkId).append("\n");
                        chunksIStore.append("\t\tSize: ").append(DigestFile.getChunkSize(fileId, chunkId)).append("\n");
                        chunksIStore.append("\t\tDesired replication degree: ").append(fileInfo.getDesiredRep()).append("\n");
                    }
                }
            }

            maxStorageSizeKB = State.st.getMaxDiskSpaceKB();
            filledB = State.st.getFilledStorageB();
        }

        long filledKB = Math.round(filledB / 1000.0);
        return filesIInitiated
                .append(chunksIStore)
                .append("Storing ").append(filledKB == 0 ? (filledB + "B") : (filledKB + "KB"))
                .append(" of a maximum of ")
                .append(maxStorageSizeKB < 0 ? "infinite " : maxStorageSizeKB).append("KB.")
                .toString();
    }

    @Override
    public String toString() {
        return
                "Peer id: " + this.id + "\n" +
                        "Service access point: " + this.accessPoint + "\n";
    }

    private static void usage() {
        System.err.println("Usage: java\n" +
                "\tProj1 <peer id> <service access point>\n" +
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

        prog.handlePendingTasks();
        // In this function we are verifying the modified files :). Hope your day is going as intended. Bye <3
        prog.verifyModifiedFiles();

        // TODO: add to extras section
        // setup the access point
        TestInterface stub;

        try {
            stub = (TestInterface) UnicastRemoteObject.exportObject(prog, 0);
            String[] rmiinfoSplit = prog.getAccessPointName().split(":");
            prog.rmiName = rmiinfoSplit[0];

            if (rmiinfoSplit.length > 1)
                prog.registry = LocateRegistry.getRegistry("localhost", Integer.parseInt(rmiinfoSplit[1]));
            else
                prog.registry = LocateRegistry.getRegistry();

            prog.registry.bind(prog.rmiName, stub);
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
