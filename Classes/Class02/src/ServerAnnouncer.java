import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.TimerTask;

public class ServerAnnouncer extends TimerTask {
    private MulticastSocket multiSocket = null;
    private DatagramPacket packet = null;
    private  byte[] out_buf = null;

    public ServerAnnouncer(String mcast_addr, int mcast_port, int srvc_port) throws IOException {
        try {
            multiSocket = new MulticastSocket(mcast_port);
        } catch (IOException e) {
            System.err.println("Couldn't bind server to specified port, " + mcast_port);
            System.exit(1);
        }
        System.out.println("Announcing multicast on port: " + mcast_port);
        // join multicast group
        InetAddress group = InetAddress.getByName(mcast_addr);
        multiSocket.joinGroup(group);

        out_buf = ("localhost" + " " + srvc_port).getBytes();
        packet = new DatagramPacket(out_buf, out_buf.length, group, mcast_port);
    }

    public void close() {
        multiSocket.close();
    }

    @Override
    public void run() {
        final String log = new String(out_buf);
        System.out.println("Sending: " + log);
        try {
            multiSocket.send(packet);
        } catch (IOException e) {
            System.out.println("Failed sending: " + log);
        }
    }
}
