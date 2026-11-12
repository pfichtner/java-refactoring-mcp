public class Foo {
    public int run() {
        if (compute()) {
            return 1;
        }
        return 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
