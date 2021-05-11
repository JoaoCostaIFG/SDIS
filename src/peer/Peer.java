package peer;

import file.DigestFile;
import message.*;
import sender.*;
import state.FileInfo;
import state.State;
import utils.Pair;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

public class Peer implements TestInterface {
    private boolean closed = false;
    // cmd line arguments
    private final String protocolVersion;
    private final String id;
    private final String accessPoint;
    // multicast sockets
    private final SockThread MCSock;
    private final SockThread MDBSock;
    private final SockThread MDRSock;
    private final MessageHandler messageHandler;

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
        if (args.length != 9) usage();

        this.protocolVersion = args[0];
        this.id = args[1];
        // set the file dir name for the rest of the program (create it if missing)
        // and get info
        DigestFile.setFileDir(this.id);

        this.accessPoint = args[2];
        // MC
        InetAddress MC = InetAddress.getByName(args[3]);
        Integer MCPort = Integer.parseInt(args[4]);
        this.MCSock = this.createSocketThread("MC", MC, MCPort);
        // MDB
        InetAddress MDB = InetAddress.getByName(args[5]);
        Integer MDBPort = Integer.parseInt(args[6]);
        this.MDBSock = this.createSocketThread("MDB", MDB, MDBPort);
        // MDR
        InetAddress MDR = InetAddress.getByName(args[7]);
        Integer MDRPort = Integer.parseInt(args[8]);
        this.MDRSock = this.createSocketThread("MDR", MDR, MDRPort);

        if (this.protocolVersion.equals("2.0")) {
            if (State.st.isStorageFull()) this.MDBSock.leave();
            else this.MDBSock.join();
        }

        this.messageHandler = new MessageHandler(this.id, this.protocolVersion,
            this.MCSock, this.MDBSock, this.MDRSock);

        System.out.println(this);
        System.out.println("Initialized program.");
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
                    }
                    catch (RemoteException e) {
                        System.err.println("Failed to redo pending task: BACKUP " + task[1] + " " + task[2]);
                    }
                    break;
                case "RESTORE":
                    if (task.length != 2) continue;
                    try {
                        this.restore(task[1]);
                    }
                    catch (RemoteException e) {
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
                }
                catch (IOException e) {
                    newFileId = ""; // File not present, delete it
                }
                if (newFileId.equals("")) { // File not present, delete it
                    errorMsg = this.deleteFromId(oldFileId);
                    if (!errorMsg.equals("Success")) {
                        System.err.println(errorMsg + "(Peer Init)");
                        return;
                    }
                }
                else {
                    if (!newFileId.equals(oldFileId)) { // If the file changed when we were Zzz
                        try {
                            errorMsg = this.deleteFromId(oldFileId.strip());
                            if (!errorMsg.equals("Success")) {
                                System.err.println(errorMsg + "(Peer Init)");
                                return;
                            }
                            this.backup(filePath, fileInfo.getDesiredRep());
                        }
                        catch (RemoteException e) {
                            System.err.println("Fail when deleting file (Peer Init)" + filePath);
                        }
                    }
                }
            }
        }
    }

    private SockThread createSocketThread(String name, InetAddress addr, Integer port) throws IOException {
        MulticastSocket socket = new MulticastSocket(port);
        return new SockThread(name, socket, addr, port);
    }

    public void cleanup() {
        if (closed) return;
        closed = true;

        // shutdown executors
        this.testAppThreadPool.shutdownNow();

        // cleanup the access point
        if (registry != null) {
            try {
                registry.unbind(rmiName);
                UnicastRemoteObject.unexportObject(this, true);
            }
            catch (Exception e) {
                System.err.println("Failed to unregister our RMI service.");
            }
        }

        this.MCSock.close();
        this.MDBSock.close();
        this.MDRSock.close();
        try {
            State.exportMap();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mainLoop() {
        this.MCSock.start();
        this.MDBSock.start();
        this.MDRSock.start();
        Scanner scanner = new Scanner(System.in);
        String cmd;
        do {
            cmd = scanner.nextLine();
            System.out.println("CMD: " + cmd);
            String filePath = "1b.txt";
            if (cmd.equalsIgnoreCase("backup")) {
                try {
                    this.backup(filePath, 1);
                }
                catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            else if (cmd.equalsIgnoreCase("reclaim")) {
                try {
                    this.reclaim(0);
                }
                catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            else if (cmd.equalsIgnoreCase("restore")) {
                try {
                    System.out.println(this.restore(filePath));
                }
                catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } while (!cmd.equalsIgnoreCase("EXIT"));

        // shush threads
        this.MCSock.interrupt();
        this.MDBSock.interrupt();
        this.MDRSock.interrupt();
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

            if (this.protocolVersion.equals("2.0")) {
                State.st.notToDeleteAnymore(fileId);
            }

            chunks = DigestFile.divideFile(filePath, replicationDegree);
        }
        catch (IOException e) {
            State.st.rmTask(task);
            throw new RemoteException("Couldn't divide file " + filePath);
        }

        for (int i = 0; i < chunks.size(); ++i) {
            // only backup chunks that don't have the desired replication degree
            if (State.st.isChunkOk(fileId, i)) continue;

            PutChunkMsg putChunkMsg = new PutChunkMsg(this.protocolVersion, this.id,
                fileId, i, replicationDegree, chunks.get(i));
            PutChunkSender putChunkSender = new PutChunkSender(this.MDBSock, putChunkMsg, this.messageHandler);
            putChunkSender.restart();
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
        }

        catch (Exception e) {
            State.st.rmTask(task);
            throw new RemoteException("Failed to restore the file " + filePath);
        }

        if (chunkNo < 0) {
            State.st.rmTask(task);
            throw new RemoteException("File " + filePath + " is too big");
        }

        // Storing the futures to be able to restore the file after getting all the chunks (or failing
        // if a chunk is missing)
        List<Pair<Future<?>, MessageSender<? extends Message>>> senders = new ArrayList<>();
        for (int currChunk = 0; currChunk < chunkNo; ++currChunk) {
            GetChunkMsg msg = new GetChunkMsg(this.protocolVersion, this.id, fileId, currChunk);
            MessageSender<? extends Message> chunkSender;
            if (this.protocolVersion.equals("2.0")) {
                chunkSender = new GetChunkTCPSender(this.MCSock, msg, this.messageHandler);
            }
            else {
                chunkSender = new GetChunkSender(this.MCSock, msg, this.messageHandler);
            }
            senders.add(new Pair<>(this.testAppThreadPool.submit(chunkSender), chunkSender));
        }

        List<byte[]> chunks = new ArrayList<>(chunkNo);
        for (var sender : senders) {
            try {
                sender.p1.get();
            }
            catch (InterruptedException | ExecutionException e) {
                State.st.rmTask(task);
                throw new RemoteException("There was an error recovering a chunk of the file.");
            }
            int chunkNumber;
            byte[] chunk;
            if (this.protocolVersion.equals("2.0")) {
                GetChunkTCPSender getChunkTCPSender = (GetChunkTCPSender) sender.p2;
                chunkNumber = getChunkTCPSender.getMessage().getChunkNo();
            }
            else {
                GetChunkSender getChunkSender = (GetChunkSender) sender.p2;
                chunkNumber = getChunkSender.getMessage().getChunkNo();
            }

            if (!sender.p2.getSuccess()) {
                State.st.rmTask(task);
                throw new RemoteException("Failed to restore the file " + filePath +
                    " because of a missing chunk: " + chunkNumber);
            }

            if (this.protocolVersion.equals("2.0")) {
                GetChunkTCPSender getChunkTCPSender = (GetChunkTCPSender) sender.p2;
                chunk = getChunkTCPSender.getResponse();
            }
            else {
                GetChunkSender getChunkSender = (GetChunkSender) sender.p2;
                chunk = getChunkSender.getResponse();
            }
            chunks.add(chunk);
        }

        Path path = Paths.get(filePath);
        try {
            DigestFile.assembleFile(path.getFileName().toString(), chunks);
        }
        catch (IOException e) {
            State.st.rmTask(task);
            throw new RemoteException("Failed to write restored file: " + path.getFileName().toString());
        }

        State.st.rmTask(task);
        return "Restored file " + filePath + " with hash " + fileId + ".";
    }

    public String deleteFromId(String fileId) {
        if (this.protocolVersion.equals("2.0")) {
            synchronized (State.st) {
                // has initiated file
                FileInfo fileInfo = State.st.getFileInfo(fileId);
                if (fileInfo == null)
                    return "No information stored about the file with hash " + fileId;

                // update the files to delete structure with everyone we know has this file
                for (var peer : fileInfo.getPeersStoringFile())
                    State.st.addUndeletedPair(peer, fileId);
            }
        }
        // we don't want the old entry anymore
        State.st.removeFileEntry(fileId);

        DeleteMsg msg = new DeleteMsg(this.protocolVersion, this.id, fileId);
        this.MCSock.send(msg);

        return "Success";
    }

    @Override
    public String delete(String filePath) throws RemoteException {
        String fileId;
        try {
            fileId = DigestFile.getHash(filePath);
        }
        catch (IOException e) {
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
                int perceivedRep = chunkEntry.getValue().p1.size();
                boolean isStored = chunkEntry.getValue().p2;
                if (isStored && (force || perceivedRep > 1) && perceivedRep > 0) {
                    // if we have the chunk stored => delete it && decrement perceived rep.
                    long chunkSize = DigestFile.deleteChunk(fileId, chunkNo); // updates state capacity
                    State.st.decrementChunkDeg(fileId, chunkNo, this.id);
                    State.st.setAmStoringChunk(fileId, chunkNo, false);
                    currentCap -= chunkSize;

                    RemovedMsg removedMsg = new RemovedMsg(this.protocolVersion, this.id, fileId, chunkNo);
                    this.MCSock.send(removedMsg);
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
            }
            else if (State.st.getMaxDiskSpaceB() >= 0) {
                long capacityDelta = newMaxDiskSpaceB - State.st.getMaxDiskSpaceB();
                State.st.setMaxDiskSpaceB(newMaxDiskSpaceB);
                // if max capacity is unchanged or increases, we don't need to do anything
                if (capacityDelta >= 0)
                    isDone = true;
            }
            else {
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

            if (this.protocolVersion.equals("2.0")) {
                if (State.st.isStorageFull()) this.MDBSock.leave();
                else this.MDBSock.join();
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
                        filesIInitiated.append("\t\t\tID: ").append(chunkEntry.getKey())
                            .append(" - Perceived rep.: ").append(chunkEntry.getValue().p1.size()).append("\n");
                    }
                }

                else {
                    for (var chunkEntry : fileInfo.getAllChunks().entrySet()) {
                        int chunkId = chunkEntry.getKey();
                        int perceivedRep = chunkEntry.getValue().p1.size();
                        boolean isStored = chunkEntry.getValue().p2;
                        if (!isStored)  // only show chunks we are currently storing
                            continue;

                        chunksIStore.append("\tChunk ID: ").append(fileId).append(" - ").append(chunkId).append("\n");
                        chunksIStore.append("\t\tSize: ").append(DigestFile.getChunkSize(fileId, chunkId)).append("\n");
                        chunksIStore.append("\t\tDesired replication degree: ").append(fileInfo.getDesiredRep()).append("\n");
                        chunksIStore.append("\t\tPeers storing this chunk: ").append(fileInfo.getPeersStoringChunk(chunkId)).append("\n");
                        chunksIStore.append("\t\tPerceived replication degree: ").append(perceivedRep).append("\n");
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
        return "Protocol version: " + this.protocolVersion + "\n" +
            "Id: " + this.id + "\n" +
            "Service access point: " + this.accessPoint + "\n" +
            "MC: " + this.MCSock +
            "MDB: " + this.MDBSock +
            "MDR: " + this.MDRSock;
    }

    private static void usage() {
        System.err.println("""
            Usage: java -jar
            \tProj1 <protocol version> <peer id> <service access point>
            \t<MC> <MDB> <MDR>""");
        System.exit(1);
    }

    public static void main(String[] args) {
        // parse cmdline args
        Peer prog = null;

        try {
            prog = new Peer(args);
        }

        catch (IOException e) {
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
        }

        catch (Exception e) {
            System.err.println("Failed setting up the access point for use by the testing app.");
            // e.printStackTrace();
        }

        prog.mainLoop();
        System.exit(0);
    }
}
