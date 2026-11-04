public class Foo {
    private int log;

    public int run() {
        int result = choose();
        return result * 10 + log;
    }

    private int choose() {
        int value = 0;
        try {
            value = 1;
            return value;
        } finally {
            log = value + 1;
        }
    }
}
