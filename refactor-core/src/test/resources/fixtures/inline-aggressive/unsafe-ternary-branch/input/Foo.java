public class Foo {
    public int run() {
        return true ? compute() : 0;
    }

    private int compute() {
        int x = 1;
        return x + 1;
    }
}
