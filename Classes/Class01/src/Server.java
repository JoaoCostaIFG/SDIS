import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static void usage() {
        System.err.println("Usage: java Server <port number>");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length != 1) usage();

        int port_number = Integer.parseInt(args[0]);
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port_number);
        } catch (SocketException e) {
            System.err.println("Couldn't bind server to specified port, " + port_number);
            System.exit(1);
        }
        System.out.println("Bound server to port: " + port_number);

        int num_entries = 0;
        Map<String, String> dns_table = new HashMap<String, String>();
        byte[] inbuf = new byte[256];
        DatagramPacket request = new DatagramPacket(inbuf, inbuf.length);
        while (true) {
            try {
                Arrays.fill(inbuf, (byte) 0);
                socket.receive(request);
            } catch (IOException e) {
                System.err.println("Failed receiving packet. Skipping...");
                continue;
            }

            String in = new String(request.getData());
            String[] req_args = in.split("[\u0000| ]");

            System.out.println("Server: " + Arrays.toString(req_args));
            if (req_args.length == 0) {
                System.err.println("Empty request. Skipping...");
                continue;
            }

            byte[] reply_bytes = null;
            if (req_args[0].equals("REGISTER")) {
                if (req_args.length != 3) {
                    System.err.println("Register is missing arguments. Skipping...");
                    reply_bytes = "-1".getBytes();
                } else {
                    final String dns_name = req_args[1];
                    final String ip_addr = req_args[2];

                    if (dns_table.containsKey(dns_name)) { // dns name already registered
                        System.err.println("dns name already registered");
                        reply_bytes = "-1".getBytes();
                    } else { // register new dns name
                        dns_table.put(dns_name, ip_addr);
                        System.out.println("Registered: " + dns_table.get(dns_name));

                        reply_bytes = String.valueOf(num_entries).getBytes();
                        ++num_entries;
                    }
                }
            } else if (req_args[0].equals("LOOKUP")) {
                if (req_args.length != 2) {
                    System.err.println("Lookup is missing arguments. Skipping...");
                    reply_bytes = "-1".getBytes();
                } else {
                    final String dns_name = req_args[1];

                    if (dns_table.containsKey(dns_name)) { // dns name found
                        reply_bytes = (dns_name + " " + dns_table.get(dns_name)).getBytes();
                    } else { // dns name not found
                        reply_bytes = "-1".getBytes();
                    }
                }
            } else if (req_args[0].equals("CLOSE")) {
                System.out.println("Got close, closing...");
                break;
            } else {
                System.err.println("Unkown request. Skipping...");
                continue;
            }

            DatagramPacket reply = new DatagramPacket(reply_bytes, reply_bytes.length,
                    request.getAddress(), request.getPort());
            try {
                socket.send(reply);
            } catch (IOException e) {
                System.err.println("Couldn't send reply. Skipping...");
            }
        }

        System.out.println("Quitting...");
        socket.close();
    }
}
