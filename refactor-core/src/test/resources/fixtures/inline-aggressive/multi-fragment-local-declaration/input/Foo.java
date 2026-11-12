public class Foo {
    public int run() {
        int a = 0, b = compute();
        return a + b;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
