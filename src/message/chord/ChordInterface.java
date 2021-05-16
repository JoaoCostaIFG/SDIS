package message.chord;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChordInterface extends Remote {
    ChordInterface findSuccessor(int id) throws RemoteException;
    ChordInterface getPredecessor() throws RemoteException;
    void notify(ChordInterface n) throws RemoteException;
    int getId() throws RemoteException;
    String test(String arg) throws RemoteException;
}
