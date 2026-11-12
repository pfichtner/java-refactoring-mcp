public class Foo {
    private int value = 1;

    public boolean run() {
        Foo other = null;
        try {
            other.read();
            return true;
        } catch (NullPointerException expected) {
            return false;
        }
    }

    private int read() {
        return value;
    }
}
