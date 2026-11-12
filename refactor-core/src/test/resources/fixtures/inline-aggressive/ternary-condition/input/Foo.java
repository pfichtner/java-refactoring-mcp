public class Foo {
    public int run() {
        return compute() ? 1 : 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
