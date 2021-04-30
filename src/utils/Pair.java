package utils;

import java.io.Serializable;

public class Pair<T, V> implements Serializable {
    public T p1;
    public V p2;

    public Pair(T p1, V p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    @Override
    public String toString() {
        return p1.toString() + " " + p2.toString();
    }
}
