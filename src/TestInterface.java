import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TestInterface extends Remote {
    String join() throws RemoteException;

    String backup(String filePath, Integer replicationDegree) throws RemoteException;

    String restore(String filePath) throws RemoteException;

    String delete(String filePath) throws RemoteException;

    String reclaim(int maxCapacity) throws RemoteException;

    String state() throws RemoteException;
}
