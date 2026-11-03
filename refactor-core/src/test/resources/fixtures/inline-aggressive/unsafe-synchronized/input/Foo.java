public class Foo {
    public int run() {
        return guarded(2);
    }

    private synchronized int guarded(int value) {
        return value + 1;
    }
}
