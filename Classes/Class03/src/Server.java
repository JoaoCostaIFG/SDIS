import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class Server implements LookupInterface {
    private static Registry registry = null;
    private static int rmi_port;
    private final Map<String, String> dns_table = new HashMap<>();
    private int num_entries = 0;

    private static void usage() {
        System.err.println("Usage: java Server <port number> <rmi port>");
        System.exit(1);
    }

    @Override
    public String register(String dns_name, String ip_addr) throws RemoteException {
        System.out.print("REGISTER " + dns_name + " " + ip_addr + " :: ");

        String reply;
        if (dns_table.containsKey(dns_name)) { // dns name already registered
            System.err.println("dns name already registered");
            reply = "-1";
        } else { // register new dns name
            dns_table.put(dns_name, ip_addr);
            System.out.println("Registered: " + dns_table.get(dns_name));

            reply = String.valueOf(num_entries);
            ++num_entries;
        }

        System.out.println(reply);
        return reply;
    }

    @Override
    public String lookup(String dns_name) throws RemoteException {
        System.out.print("LOOKUP " + dns_name + " :: ");

        String reply;
        if (dns_table.containsKey(dns_name)) { // dns name found
            reply = dns_name + " " + dns_table.get(dns_name);
        } else { // dns name not found
            reply = "-1";
        }

        System.out.println(reply);
        return reply;
    }

    @Override
    public void close() throws RemoteException {
        System.out.println("CLOSE");

        try {
            registry.unbind("Lookup");
        } catch (NotBoundException e) {
            System.err.println("Server exception: " + e.toString());
            System.exit(1);
        }
        System.exit(0);
    }

    @Override
    public String hello() throws RemoteException {
        System.out.print("HELLO :: ");
        String reply = "Server running RMI on port: " + rmi_port + ". Available methods: REGISTER LOOKUP CLOSE HELLO";
        System.out.println(reply);
        return reply;
    }

    public static void main(String[] args) {
        if (args.length != 2) usage();
        int port_number = Integer.parseInt(args[0]);
        rmi_port = Integer.parseInt(args[1]);

        try {
            Server obj = new Server();
            LookupInterface stub = (LookupInterface) UnicastRemoteObject.exportObject(obj, port_number);

            // bind the remote object's stub in the registry
            registry = LocateRegistry.getRegistry(rmi_port);
            registry.bind("Lookup", stub);

            System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
