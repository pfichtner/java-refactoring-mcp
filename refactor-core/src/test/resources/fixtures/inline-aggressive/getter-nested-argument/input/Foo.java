public class Foo {
    private int value = 21;
    private int counter = 0;

    public int run() {
        return tick() + getValue();
    }

    private int tick() {
        return counter++;
    }

    private int getValue() {
        return this.value;
    }
}
