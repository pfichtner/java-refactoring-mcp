public class Foo {
    public int run() {
        return countdown(3);
    }

    private int countdown(int value) {
        if (value <= 0) {
            return 0;
        }
        return countdown(value - 1);
    }
}
