public class Foo {
    public int run() {
        StringBuilder sb = new StringBuilder(capacity());
        sb.append("x");
        return sb.length();
    }

    private int capacity() {
        return 5;
    }
}
