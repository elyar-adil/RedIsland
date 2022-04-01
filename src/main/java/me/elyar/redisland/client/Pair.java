package me.elyar.redisland.client;

public class Pair<F, S> {
    private F first;
    private S second;

    public void setFirst(F first) {
        this.first = first;
    }

    public void setSecond(S second) {
        this.second = second;
    }

    public S getSecond() {
        return second;
    }

    public F getFirst() {
        return first;
    }

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }

    public int hashCode() {
        byte var1 = 7;
        int var2 = 31 * var1 + (this.first != null ? this.first.hashCode() : 0);
        var2 = 31 * var2 + (this.second != null ? this.second.hashCode() : 0);
        return var2;
    }

    public boolean equals(Object var1) {
        if (this == var1) {
            return true;
        } else if (!(var1 instanceof Pair)) {
            return false;
        } else {
            Pair var2 = (Pair)var1;
            if (this.first != null) {
                if (!this.first.equals(var2.first)) {
                    return false;
                }
            } else if (var2.first != null) {
                return false;
            }

            if (this.second != null) {
                if (!this.second.equals(var2.second)) {
                    return false;
                }
            } else if (var2.second != null) {
                return false;
            }

            return true;
        }
    }

}
