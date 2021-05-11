package message;

import file.DigestFile;
import utils.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ChunkMsg extends Message {
    public static final String type = "CHUNK";
    private final int chunkNo;
    private byte[] chunk;

    public ChunkMsg(String version, String id, String fileId, int chunkNo, byte[] chunk) {
        super(version, id, fileId);
        this.header = version + " " +
            type + " " +
            id + " " +
            fileId + " " +
            chunkNo + " " +
            Message.CRLF + Message.CRLF;
        this.chunkNo = chunkNo;
        this.chunk = chunk;
    }

    public ChunkMsg(String version, String id, String fileId, int chunkNo) {
        this(version, id, fileId, chunkNo, new byte[0]);

        try {
            this.chunk = DigestFile.readChunk(fileId, chunkNo);
        }

        catch (IOException e) {
            e.printStackTrace(); // TODO Fail if chunk isn't here
        }
    }

    public void setTCPAddr(String ip, int port) {
        this.chunk = (ip + " " + port).getBytes();
    }

    public Pair<String, Integer> getTCP() {
        String[] tcpInfo = new String(this.chunk).split(" ");
        return new Pair<>(tcpInfo[0], Integer.parseInt(tcpInfo[1]));
    }

    public byte[] getChunk() {
        return chunk;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    @Override
    public byte[] getContent() {
        byte[] headerBytes = super.getContent();
        byte[] bodyBytes = this.chunk;

        return ByteBuffer.allocate(headerBytes.length + bodyBytes.length)
            .put(headerBytes).put(bodyBytes).array();
    }

    @Override
    public String getSockName() {
        return "MDR";
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public int getHeaderLen() {
        return 5;
    }

    @Override
    public String toString() {
        return type + " " + this.fileId + " chunkno. " + this.chunkNo + " from " + super.id;
    }
}
