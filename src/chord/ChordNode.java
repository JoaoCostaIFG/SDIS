package chord;

import java.util.HashMap;

public class ChordNode {
    private int id;  // The peer's unique identifier
    private int m;   // Number of bits of the addressing space
    private HashMap<Integer, ChordNode> fingerTable;
    private ChordNode predecessor;
    private ChordNode successor;

    public ChordNode() {

    }

    public void join() {

    }

    public void stabilize() {

    }

    public void notify(int id) {

    }

    public void fix_fingers() {

    }

    public ChordNode closestPrecidingNode(int id) {
        for (int i = m; i >= 1; i--)
            if (fingerTable.get(i).id > this.id && fingerTable.get(i).id < id)
                return fingerTable.get(i);

        return this;
    }

    public ChordNode findSuccessor(int id) {
        if (id > this.id && id < successor.id)
            return successor;

        else
            // Forward the query around the circle
            return closestPrecidingNode(id).findSuccessor(id);
    }

    public void checkPredecessor() {

    }
}
