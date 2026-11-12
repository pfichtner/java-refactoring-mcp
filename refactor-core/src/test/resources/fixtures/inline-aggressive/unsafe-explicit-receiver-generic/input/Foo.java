public class Foo<T> {
    private int value = 1;

    public int run(Foo<T> other) {
        return other.read();
    }

    private int read() {
        return value;
    }
}
