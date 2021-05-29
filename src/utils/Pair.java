package utils;

import java.io.Serializable;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(p1, pair.p1) && Objects.equals(p2, pair.p2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p1, p2);
    }
}
