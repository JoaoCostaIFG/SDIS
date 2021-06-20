import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    private static void usage() {
        System.err.println("Usage: java Client <host> <port> <oper> <opnd>*");
        System.exit(1);
    }

    public void client(String host, int port, String oper, String[] opnd) {
        // no reply if close
        //if (oper.equals("close"))  return;

        LookupInterface stub = null;
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            stub = (LookupInterface) registry.lookup("Lookup");
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
            return;
        }

        try {
            String response;
            if (oper.equals("REGISTER")) {
                response = stub.register(opnd[0], opnd[1]);
            }
            else if (oper.equals("LOOKUP")) {
                response = stub.lookup(opnd[0]);
            }
            else { // CLOSE
                stub.close();
                response = "Closing";
            }
            System.out.println(response);
        } catch (RemoteException e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) usage();

        // parse arguments
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String oper = args[2].toUpperCase();
        // operands
        String[] opnd = new String[2];
        String req_str = oper.toUpperCase();
        if (oper.equals("REGISTER")) {
            opnd[0] = args[3];
            if (args.length != 5) usage();
            opnd[1] = args[4];
            req_str += " " + opnd[0] + " " + opnd[1];
        } else if (oper.equals("LOOKUP")) {
            opnd[0] = args[3];
            if (args.length != 4) usage();
            req_str += " " + opnd[0];
        } else if (oper.equals("CLOSE")) {
            if (args.length != 3) usage();
        } else {
            System.err.println("Unkown operation: " + oper);
            usage();
        }
        // log
        System.out.println("Client: " + host + " " + port);

        System.out.print(req_str + " :: ");
        new Client().client(host, port, oper, opnd);
    }
}
