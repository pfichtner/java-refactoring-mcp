public class Foo {
    private static final int LIMIT = 50;

    public int run() {
        return compute();
    }

    private int compute() {
        return LIMIT * 2;
    }
}
