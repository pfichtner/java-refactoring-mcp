public class Foo {
    public int run() {
        int a = sideEffect(), b = compute();
        return a + b;
    }

    private int sideEffect() {
        return 1;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
