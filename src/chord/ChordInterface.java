package chord;

import utils.Pair;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface ChordInterface extends Remote {
    int getId() throws RemoteException;
    ChordInterface getPredecessor() throws RemoteException;
    ChordInterface getSuccessor() throws RemoteException;

    ChordInterface[] getSuccessors() throws RemoteException;

    ChordInterface findSuccessor(int id) throws RemoteException;
    ChordInterface findPredecessor(int id) throws RemoteException;
    ChordInterface closestPrecedingNode(int id) throws RemoteException;


    void notify(ChordInterface n) throws RemoteException;

    InetAddress getAddress() throws RemoteException;
    int getPort() throws RemoteException;
    Map<Pair<String, Integer>, Integer> getStoredChunksIds() throws RemoteException;
}
