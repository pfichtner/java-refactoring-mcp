public class Foo {
    private int value = 1;

    class Inner {
        int read() {
            return value;
        }

        int run(Inner other) {
            return other.read();
        }
    }
}
