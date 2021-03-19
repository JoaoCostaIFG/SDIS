package File;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class DigestFile {
    final static Integer CHUNK_LEN = 256;
    private static final int MAX_CHUNK_SIZE = 64000;
    private static final int MAX_CHUNK_NUM = 999999;
    final static String FILE_DIR = "." + File.separator + "files" + File.separator;

    private static String getBitString(String filename) throws IOException {
        Path file = Paths.get(FILE_DIR + filename);
        FileInputStream inputStream = new FileInputStream(FILE_DIR + filename);

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

    public static String getHash(String filename) throws IOException {
        String bitString = getBitString(filename);
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

    private static boolean surpassesMaxChunks(String filename) throws IOException {
        Path file = Paths.get(FILE_DIR + filename);
        return ((Files.size(file) / MAX_CHUNK_SIZE) > MAX_CHUNK_NUM);
    }

    public static void writeChunk(String chunkpath, byte[] b, int n) throws IOException {
        String path = FILE_DIR + File.separator + chunkpath;
        File f = new File(path);
        f.getParentFile().mkdirs();
        if (!f.createNewFile()) return;
        if (n >= 0) {
            try (FileOutputStream chunk = new FileOutputStream(path)) {
                chunk.write(b, 0, n);
            } catch (Exception e) {
                System.out.println("no write :(" + chunkpath + " " + n);
            }
        }
    }

    public static byte[] readChunk(String filename, int chunkNo) throws IOException {
        FileInputStream inputFile = new FileInputStream(FILE_DIR + getHash(filename) +
                File.separator + chunkNo);
        byte[] b = new byte[MAX_CHUNK_SIZE];
        inputFile.read(b, 0, MAX_CHUNK_SIZE);
        return b;
    }

    public static void divideFile(String filename) throws IOException {
        String fileId = getHash(filename);
        FileInputStream inputFile = new FileInputStream(FILE_DIR + filename);
        byte[] b = new byte[MAX_CHUNK_SIZE];
        int n, i=0;

        if (surpassesMaxChunks(filename))
            throw new MasNaoTeVouAlocar();

        while ((n = inputFile.read(b, 0, MAX_CHUNK_SIZE)) >= MAX_CHUNK_SIZE) {
            final String chunkpath = fileId + File.separator + i;
            writeChunk(chunkpath, b, n);
            ++i;
        }

        final String chunkpath = fileId + File.separator + i;
        writeChunk(chunkpath, b, n);
    }

    public static void assembleFile(String filename, String file_id) throws IOException {
        byte[] b = new byte[MAX_CHUNK_SIZE];
        int i = 0;
        boolean done = false;

        File f = new File(FILE_DIR + filename);
        f.getParentFile().mkdirs();
        if (!f.createNewFile()) return;
        FileOutputStream file = new FileOutputStream(f);;

        while (!done) {
            String chunkpath = FILE_DIR + file_id + File.separator + i;
            try {
                FileInputStream inputStream = new FileInputStream(chunkpath);
                int n = inputStream.read(b);
                file.write(b, 0, n);
            } catch (FileNotFoundException e) {
                done = true;
            }
            ++i;
        }
    }

    public static void main(String[] args) {
        try {
            String filename = "filename.rar";
            // String h = getHash(filename);
            // divideFile(filename);

            String id = "416ebf6f9e407ba10294e58cbcdc1ef55b0920cd6fd6255fe6767528ddf50aba";
            assembleFile(filename, id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
