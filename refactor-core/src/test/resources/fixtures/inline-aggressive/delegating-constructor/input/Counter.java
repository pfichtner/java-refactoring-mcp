public class Counter {
    private int count;

    public Counter() {
        this(0);
    }

    public Counter(int initial) {
        this.count = initial;
    }

    public int value() {
        return count;
    }
}
