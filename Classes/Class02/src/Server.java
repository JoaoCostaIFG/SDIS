import java.io.IOException;
import java.net.*;
import java.util.*;

/*
Multicast allows communication between different containers to be used on different
computers (in this case, you should use different terminals for this purpose).

The containers should be specified by a port and an IP address within the range
224.0.0.0 and 239.255.255.255, which are IPs reserved for multicast communications.

Thus, as in Lab1, you should create a client-server system. The server should
periodically send its IP and port to the multicast group to know that it is
working correctly. Clients should join that group and then send messages with
the desired requests. In this case, you should use MulticastSockets instead
of DatagramSockets to achieve the desired multicast communication.
To do the "register" and "lookup" operations, you should use
the same method that you used in Lab1.

To create a more robust system, I advise you to use more than one thread.
You can use the functions provided by Timer and TimerTask (package “java.util”)
or ScheduledThreadPoolExecutor (package “java.util.concurrent”). The last one
is more sophisticated but also more difficult to implement and use.
*/

public class Server {
    private DatagramSocket uniSocket = null;

    private static void usage() {
        System.err.println("Usage: java Server <port number>");
        System.exit(1);
    }

    private void server(String[] args) throws IOException {
        // parse args
        if (args.length != 3) usage();
        int srvc_port = Integer.parseInt(args[0]);
        String mcast_addr = args[1];
        int mcast_port = Integer.parseInt(args[2]);

        // create unicast socket
        try {
            uniSocket = new DatagramSocket(srvc_port);
        } catch (SocketException e) {
            System.err.println("Couldn't bind server to specified port, " + srvc_port);
            System.exit(1);
        }
        System.out.println("Bound server to port: " + srvc_port);

        // create multicast socket and bind to given port number
        // thread sending ip + port every sec
        ServerAnnouncer sa = new ServerAnnouncer(mcast_addr, mcast_port, srvc_port);
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(sa, 0, 1000); // run every sec

        // main loop
        int num_entries = 0;
        Map<String, String> dns_table = new HashMap<String, String>();
        byte[] inbuf = new byte[256];
        DatagramPacket request = new DatagramPacket(inbuf, inbuf.length);
        while (true) {
            // receive request packet
            try {
                Arrays.fill(inbuf, (byte) 0);
                uniSocket.receive(request);
            } catch (IOException e) {
                System.err.println("Failed receiving packet. Skipping...");
                continue;
            }

            // parse packet
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

            // send reply packet
            DatagramPacket reply = new DatagramPacket(reply_bytes, reply_bytes.length,
                    request.getAddress(), request.getPort());
            try {
                uniSocket.send(reply);
            } catch (IOException e) {
                System.err.println("Couldn't send reply. Skipping...");
            }
        }

        System.out.println("Quitting...");
        timer.cancel();
        sa.close();
        uniSocket.close();
    }

    public static void main(String[] args) throws IOException {
        new Server().server(args);
    }
}
