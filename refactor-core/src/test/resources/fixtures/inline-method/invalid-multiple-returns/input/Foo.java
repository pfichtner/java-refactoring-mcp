public class Foo {
    public int run() {
        int result = compute(5);
        return result;
    }

    private int compute(int n) {
        int doubled = n * 2;
        return doubled;
    }
}
