package chord;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ChordInterface extends Remote {
    int getId() throws RemoteException;
    ChordInterface getPredecessor() throws RemoteException;
    ChordInterface getSuccessor() throws RemoteException;

    ChordInterface findSuccessor(int id) throws RemoteException;
    ChordInterface findPredecessor(int id) throws RemoteException;
    ChordInterface closestPrecedingFinger(int id) throws RemoteException;

    void notify(ChordInterface n) throws RemoteException;
}
