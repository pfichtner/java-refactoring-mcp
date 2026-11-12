public class Foo {
    private int value = 1;
    private int calls;

    public int run() {
        Foo other = next();
        other.read();
        return calls;
    }

    private Foo next() {
        calls++;
        return this;
    }

    private int read() {
        return value + value;
    }
}
