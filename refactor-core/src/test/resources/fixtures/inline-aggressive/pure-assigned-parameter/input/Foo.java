public class Foo {
    public int run() {
        int value = 1;
        int result = bump(value);
        return value * 100 + result;
    }

    private int bump(int value) {
        return ++value;
    }
}
