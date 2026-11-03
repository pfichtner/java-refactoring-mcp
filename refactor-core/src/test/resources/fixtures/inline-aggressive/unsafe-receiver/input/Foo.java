public class Foo {
    private int value = 1;

    public int run(Foo other) {
        return other.read();
    }

    private int read() {
        return value;
    }
}
