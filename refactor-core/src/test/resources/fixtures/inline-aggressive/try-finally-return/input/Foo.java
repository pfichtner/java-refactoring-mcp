public class Foo {
    public int run() {
        return choose();
    }

    private int choose() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }
}
