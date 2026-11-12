public class Foo {
    private int value;

    public int run() {
        value = compute();
        return value;
    }

    private int compute() {
        return 7;
    }
}
