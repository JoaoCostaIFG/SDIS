package utils;

import java.util.Objects;

public class Triplet<T, V, U> {
    public T p1;
    public V p2;
    public U p3;

    @Override
    public String toString() {
        return p1.toString() + " " + p2.toString() + " " + p3.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triplet<?, ?, ?> triplet = (Triplet<?, ?, ?>) o;
        return Objects.equals(p1, triplet.p1) && Objects.equals(p2, triplet.p2) && Objects.equals(p3, triplet.p3);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p1, p2, p3);
    }
}
