package file;

import chord.ChordNode;
import state.State;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.Math.pow;

public class DigestFile {
    public static final int MAX_CHUNK_SIZE = 30000;  // 64000;

    private final static Integer CHUNK_LEN = 256;
    private static final int MAX_CHUNK_NUM = 999999;

    public static String PEER_DIR = "." + File.separator + "peer" + File.separator;
    public static String FILE_DIR = PEER_DIR + "stored" + File.separator;
    public static String RESTORE_DIR = PEER_DIR + "restored" + File.separator;

    public static void setFileDir(String id) {
        PEER_DIR = "." + File.separator + ("peer-" + id) + File.separator;
        File peerDir = new File(PEER_DIR);
        peerDir.mkdirs();

        FILE_DIR = PEER_DIR + "stored" + File.separator;
        File fileDir = new File(FILE_DIR);
        fileDir.mkdirs();

        RESTORE_DIR = PEER_DIR + "restored" + File.separator;
        File restDir = new File(RESTORE_DIR);
        restDir.mkdirs();
    }

    /* file metadata used to get a hash */
    private static String getBitString(Path file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file.toFile());

        byte[] b = new byte[256];
        int len = inputStream.read(b, 0, CHUNK_LEN); // Read first 256 bytes

        // Hash with absolute path, owner, last modified time and first 256 bytes
        StringBuilder bitString = new StringBuilder(
                file.toAbsolutePath().toString() +
                        Files.getOwner(file) +
                        Files.getLastModifiedTime(file)
        );
        for (int i = 0; i < len; ++i) // Add 256 bytes
            bitString.append((char) b[i]);

        return bitString.toString();
    }

    /* get the hash that identifies a given file */
    public static String getHash(String filename) throws IOException {
        String bitString = getBitString(Paths.get(filename));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(bitString.getBytes(StandardCharsets.US_ASCII));
            StringBuilder r = new StringBuilder();
            for (byte b : bytes)
                r.append(String.format("%02x", b));
            return r.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    /* returns id of a chunk */
    public static int getId(String fileId, int i) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((fileId + i).getBytes(StandardCharsets.US_ASCII));
            ByteBuffer wrapped = ByteBuffer.wrap(hash);
            int div = (int) Math.floor(pow(2, ChordNode.m));
            return Math.floorMod(wrapped.getInt(), div);
        } catch (NoSuchAlgorithmException ignored) {
        }
        return -1;
    }

    /* checks if a file needs more chunks to be stored than the maximum allowed */
    private static boolean surpassesMaxChunks(Path filePath) throws IOException {
        return ((Files.size(filePath) / MAX_CHUNK_SIZE) > MAX_CHUNK_NUM);
    }

    /* returns the number of chunks used to store a given file. -1 if the file is too big */
    public static int getChunkCount(String filename) throws IOException {
        Path filePath = Paths.get(filename);
        if (surpassesMaxChunks(filePath)) return -1;
        return (int) ((Files.size(filePath) / MAX_CHUNK_SIZE) + 1);
    }

    /*  deletes a directory (that represents a file by containing its chunks) and its contents
     *  returns true when the entry was present in the map
     */
    public static boolean deleteFile(String fileId) {
        if (State.st.getFileInfo(fileId) == null) return false;
        State.st.removeFileEntry(fileId);

        File fileDir = new File(FILE_DIR + fileId);
        if (fileDir.listFiles() != null) {
            // to delete a directory, the directory must be empty
            for (File f : Objects.requireNonNull(fileDir.listFiles())) {
                State.st.updateStorageSize(-f.length());
                f.delete();
            }
        }

        fileDir.delete();
        return true;
    }

    public static long deleteChunk(String fileId, Integer chunkNo) {
        File chunk = new File(FILE_DIR + fileId + File.separator + chunkNo.toString());
        long chunkSize = chunk.length();
        State.st.updateStorageSize(-chunkSize);
        chunk.delete();

        // delete file dir if empty
        File fileDir = new File(FILE_DIR + fileId);
        if (Objects.requireNonNull(fileDir.listFiles()).length == 0)
            fileDir.delete();

        return chunkSize;
    }

    public static long getChunkSize(String fileId, Integer chunkNo) {
        Path path = Paths.get(FILE_DIR + fileId + File.separator + chunkNo.toString());
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    public static long getStorageSize() {
        long ret = 0;
        File fileDir = new File(FILE_DIR);
        if (fileDir.listFiles() == null) return ret;

        // to delete a directory, the directory must be empty
        for (File file : Objects.requireNonNull(fileDir.listFiles())) {
            if (file.isDirectory()) {
                for (File chunk : Objects.requireNonNull(file.listFiles())) {
                    ret += chunk.length();
                }
            }
        }
        return ret;
    }

    /* Write a chunk to a file */
    public static void writeChunk(String fileId, Integer chunkNo, byte[] b, int n) throws IOException {
        String path = FILE_DIR + fileId + File.separator + chunkNo;
        File f = new File(path);
        f.getParentFile().mkdirs();
        if (!f.createNewFile()) return;
        if (n >= 0) {
            try (FileOutputStream chunk = new FileOutputStream(path)) {
                chunk.write(b, 0, n);
            } catch (Exception e) {
                System.out.println("no write :(" + path + " " + n);
            }
        }
    }

    /* reads the contents of a chunk */
    public static byte[] readChunk(String chunkpath) throws IOException {
        FileInputStream inputFile = new FileInputStream(FILE_DIR + chunkpath);
        byte[] b = new byte[MAX_CHUNK_SIZE];
        int len = inputFile.read(b, 0, MAX_CHUNK_SIZE);
        if (len <= 0)
            return new byte[0];
        return Arrays.copyOfRange(b, 0, len);
    }

    public static byte[] readChunk(String fileId, int chunkNo) throws IOException {
        return readChunk(fileId + File.separator + chunkNo);
    }

    /* divide a file into chunks */
    public static List<byte[]> divideFile(String filename, int replicationDegree) throws IOException {
        Path filePath = Paths.get(filename);
        if (surpassesMaxChunks(filePath))
            throw new IOException("File is too big (mas não te vou alocar).");

        String fileId = getHash(filename);
        long fileSize = filePath.toFile().length();
        FileInputStream inputStream = new FileInputStream(filePath.toFile());

        State.st.addFileEntry(fileId, filename, replicationDegree); // >:( // >:(

        List<byte[]> ret = new ArrayList<>();
        int i = 0;
        long remainingSize = fileSize;
        while (remainingSize > 0) {
            int chunkSize;
            if (MAX_CHUNK_SIZE <= remainingSize) {
                chunkSize = MAX_CHUNK_SIZE;
            } else {
                chunkSize = (int) remainingSize;
            }
            byte[] b = new byte[chunkSize];
            inputStream.read(b, 0, chunkSize);

            remainingSize -= chunkSize;

            State.st.declareChunk(fileId, i++);  // only declares if it isn't declared yet
            ret.add(b);
        }

        if (fileSize % MAX_CHUNK_SIZE == 0) {
            State.st.declareChunk(fileId, i);  // only declares if it isn't declared yet
            ret.add(new byte[0]);
        }

        return ret;
    }

    public static byte[] divideFileChunk(String filename, int chunkNo) throws IOException {
        Path filePath = Paths.get(filename);
        if (surpassesMaxChunks(filePath))
            throw new IOException("File is too big (mas não te vou alocar).");

        FileInputStream inputStream = new FileInputStream(filePath.toFile());

        long fileSize = filePath.toFile().length();
        long toSkip = (long) chunkNo * MAX_CHUNK_SIZE;
        long chunkSize = fileSize - toSkip;
        if (chunkSize > MAX_CHUNK_SIZE)
            chunkSize = MAX_CHUNK_SIZE;

        if (chunkSize <= 0)
            return new byte[0];

        inputStream.skip(toSkip);

        byte[] ret = new byte[(int) chunkSize];
        inputStream.read(ret, 0, (int) chunkSize);
        return ret;
    }

    /* reassemble a file from its chunks */
    public static void assembleFile(String filename, List<byte[]> chunks) throws IOException {
        File f = new File(RESTORE_DIR + filename);
        f.getParentFile().mkdirs();
        f.createNewFile();
        FileOutputStream file = new FileOutputStream(f);

        for (byte[] chunk : chunks)
            file.write(chunk, 0, chunk.length);
    }

    /* returns whether or not we have this chunk stored */
    public static boolean hasChunkInFileSystem(String fileId, Integer chunkNo) {
        File file = new File(FILE_DIR + fileId + File.separator + chunkNo);
        return file.exists();
    }
}
