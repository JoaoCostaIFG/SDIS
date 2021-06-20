import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Client {
    private static void usage() {
        System.err.println("Usage: java Client <host> <port> <oper> <opnd>*");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 3) usage();

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String oper = args[2];
        // operands
        String[] opnd = new String[2];
        String req_str = oper.toUpperCase();
        if (oper.equals("register")) {
            opnd[0] = args[3];
            if (args.length != 5) usage();
            opnd[1] = args[4];
            req_str += " " + opnd[0] + " " + opnd[1];
        }else if (oper.equals("lookup")) {
            opnd[0] = args[3];
            if (args.length != 4) usage();
            req_str += " " + opnd[0];
        }
        else if (oper.equals("close")) {
            if (args.length != 3) usage();
        }
        else {
            System.err.println("Unkown operation: " + oper);
            usage();
        }

        // log
        System.out.println("Client: " + host + " " + port + " " + req_str);

        // open socket
        byte[] outbuf = req_str.getBytes();
        DatagramPacket req = null;
        DatagramSocket socket = null;
        try {
            req = new DatagramPacket(outbuf, outbuf.length, InetAddress.getByName(host), port);
            socket = new DatagramSocket();
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Couldn't create packet/socket.");
            System.exit(1);
        }

        // send request
        try {
            socket.send(req);
        } catch (IOException e) {
            System.err.println("Failed sending packet.");
            usage();
        }

        // no reply if close
        if (oper.equals("close"))  return;
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
                System.out.printf("Client: %s : %s", req_str, reply_args[0].equals("-1") ? "ERROR" : Arrays.toString(reply_args));
        } catch (IOException e) {
            System.err.println("Couldn't get answer.");
        }

        socket.close();
    }
}
