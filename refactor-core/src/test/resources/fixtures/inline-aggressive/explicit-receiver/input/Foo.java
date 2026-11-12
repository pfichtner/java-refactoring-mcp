public class Foo {
    private int value = 1;

    public int run() {
        Foo other = new Foo();
        other.value = 7;
        return other.read();
    }

    private int read() {
        return helper() + value;
    }

    private int helper() {
        return 2;
    }
}
