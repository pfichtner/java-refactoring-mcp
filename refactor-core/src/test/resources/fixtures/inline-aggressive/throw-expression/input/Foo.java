public class Foo {
    public int run() {
        try {
            throw compute();
        } catch (IllegalArgumentException e) {
            return e.getMessage().length();
        }
    }

    private IllegalArgumentException compute() {
        String msg = "error";
        return new IllegalArgumentException(msg);
    }
}
