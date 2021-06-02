package message;

import file.DigestFile;

import java.io.IOException;
import java.net.InetAddress;


public class ChunkMsg extends Message {
    public static final String type = "CHUNK";
    private final int chunkNo;
    private byte[] chunk;

    public ChunkMsg(String fileId, int chunkNo, InetAddress sourceDest, int sourcePort, Integer destId)  {
        super(fileId, sourceDest, sourcePort, destId);

        this.chunkNo = chunkNo;
        try {
            this.chunk = DigestFile.readChunk(fileId, chunkNo);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] getChunk() {
        return chunk;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + (Message.DEBUG_MODE ? " FileId: " + fileId : "")
                + " ChunkSize:" + chunk.length + " ChunkNo:" + chunkNo;
    }
}
