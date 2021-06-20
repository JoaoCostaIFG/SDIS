import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LookupInterface extends Remote {
    String register(String dns_name, String ip_addr) throws RemoteException;

    String lookup(String dns_name) throws RemoteException;

    void close() throws RemoteException;

    String hello() throws RemoteException;
}
