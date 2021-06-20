import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Client {
    private static DatagramSocket socket = null;

    private static void usage() {
        System.err.println("Usage: java Client <mcast_addr> <mcast_port> <oper> <opnd>*");
        System.exit(1);
    }

    private String getServerAddr(String mcast_addr, int mcast_port) throws IOException {
        // wait for srcv addr and port
        MulticastSocket multiSocket = new MulticastSocket(mcast_port);
        InetAddress group = InetAddress.getByName(mcast_addr);
        multiSocket.joinGroup(group);
        byte[] multicast_buf = new byte[256];
        // receive packet
        DatagramPacket packet = new DatagramPacket(multicast_buf, multicast_buf.length, group, mcast_port);
        multiSocket.receive(packet);
        // cleanup
        multiSocket.leaveGroup(group);
        multiSocket.close();

        return new String(multicast_buf);
    }

    private void client(String[] args) throws IOException {
        if (args.length < 3) usage();

        // parse arguments
        String mcast_addr = args[0];
        int mcast_port = Integer.parseInt(args[1]);
        String oper = args[2];
        // operands
        String[] opnd = new String[2];
        String req_str = oper.toUpperCase();
        if (oper.equals("register")) {
            opnd[0] = args[3];
            if (args.length != 5) usage();
            opnd[1] = args[4];
            req_str += " " + opnd[0] + " " + opnd[1];
        } else if (oper.equals("lookup")) {
            opnd[0] = args[3];
            if (args.length != 4) usage();
            req_str += " " + opnd[0];
        } else if (oper.equals("close")) {
            if (args.length != 3) usage();
        } else {
            System.err.println("Unkown operation: " + oper);
            usage();
        }
        // log
        System.out.println("Client: " + mcast_addr + " " + mcast_port + " " + req_str);

        // get server srvc addr and port
        String[] server_addr = getServerAddr(mcast_addr, mcast_port).split("[\u0000| ]");
        String host = server_addr[0];
        int port = Integer.parseInt(server_addr[1]);

        // open socket
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            System.err.println("Couldn't create packet/socket.");
            System.exit(1);
        }
        // create request datagram
        byte[] outbuf = req_str.getBytes();
        DatagramPacket req = new DatagramPacket(outbuf, outbuf.length, InetAddress.getByName(host), port);

        // send request
        try {
            socket.send(req);
        } catch (IOException e) {
            System.err.println("Failed sending packet.");
            usage();
        }

        // no reply if close
        if (oper.equals("close")) return;
        // get reply
        byte[] inbuf = new byte[256];
        Arrays.fill(inbuf, (byte) 0);
        DatagramPacket reply = new DatagramPacket(inbuf, inbuf.length);
        try {
            socket.setSoTimeout(5000);
            socket.receive(reply);
            String in = new String(reply.getData());
            String[] reply_args = in.split("[\u0000| ]");
            if (reply_args.length == 0)
                System.err.println("Empty reply");
            else
                System.out.printf("Client: %s : %s\n", req_str, reply_args[0].equals("-1") ? "ERROR" : Arrays.toString(reply_args));
        } catch (IOException e) {
            System.err.println("Couldn't get answer.");
        }

        socket.close();
    }

    public static void main(String[] args) throws IOException {
        new Client().client(args);
    }
}
