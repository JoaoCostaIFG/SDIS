import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {
    private static void usage() {
        System.err.println("Usage: java TestApp <AccessPoint> <BACKUP|RESTORE|DELETE|RECLAIM|STATE|JOIN> [opnd_1 [opnd_2]]\n");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 4) usage();

        String rmiinfo = args[0];
        String[] rmiinfoSplit = rmiinfo.split(":");
        String rminame = rmiinfoSplit[0];

        TestInterface stub = null;
        try {
            Registry registry;
            if (rmiinfoSplit.length > 1)
                registry = LocateRegistry.getRegistry("localhost", Integer.parseInt(rmiinfoSplit[1]));
            else
                registry = LocateRegistry.getRegistry();
            stub = (TestInterface) registry.lookup(rminame);
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Couldn't find/get the desired remote object.");
            e.printStackTrace();
            System.exit(1);
        }
        assert stub != null;

        String reply = null;
        String oper = args[1];
        try {
            switch (oper.toUpperCase()) {
                case "BACKUP":
                    if (args.length != 4) usage();
                    String filePath = args[2];
                    int replicationDegree = Integer.parseInt(args[3]);
                    System.out.println("BACKUP " + filePath + " " + replicationDegree);

                    reply = stub.backup(filePath, replicationDegree);
                    break;
                case "RESTORE":
                    if (args.length != 3) usage();
                    filePath = args[2];
                    System.out.println("RESTORE " + filePath);

                    reply = stub.restore(filePath);
                    break;
                case "DELETE":
                    if (args.length != 3) usage();
                    filePath = args[2];
                    System.out.println("DELETE " + filePath);

                    reply = stub.delete(filePath);
                    break;
                case "RECLAIM":
                    if (args.length != 3) usage();
                    int maxCapacity = Integer.parseInt(args[2]);
                    System.out.println("RECLAIM " + maxCapacity);

                    reply = stub.reclaim(maxCapacity);
                    break;
                case "STATE":
                    if (args.length != 2) usage();
                    System.out.println("STATE");

                    reply = stub.state();
                    break;
                case "JOIN":
                    if (args.length != 2) usage();
                    System.out.println("JOIN");

                    reply = stub.join();
                    break;

                default:
                    System.err.println("Unknown operation: " + oper);
                    usage();
                    break;
            }
            assert reply != null;
            System.out.println(reply);
        } catch (RemoteException e) {
            System.err.println("Failed: " + e.getMessage());
        }
    }
}
