public class Foo {
    public int run() {
        return this.pick(3);
    }

    private int pick(int value) {
        return value + 10;
    }

    private String pick(String value) {
        return value;
    }
}
