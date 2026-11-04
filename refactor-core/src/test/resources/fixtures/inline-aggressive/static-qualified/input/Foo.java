public class Foo {
    public int run() {
        return Foo.compute(2);
    }

    private static int compute(int value) {
        return value * 3;
    }
}
