public class Foo {
    public int run() {
        int value = 5;
        return twice(value);
    }

    private int twice(int value) {
        return value + value;
    }
}
