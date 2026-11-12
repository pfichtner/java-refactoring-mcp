public class Chain {
    public int start() {
        return first(1);
    }

    private int first(int x) {
        return second(x + 1);
    }

    private int second(int x) {
        return third(x * 2);
    }

    private int third(int x) {
        return x - 3;
    }
}
