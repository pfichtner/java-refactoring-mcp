public class Foo {
    public int run() {
        return makeArray()[compute()];
    }

    private int[] makeArray() {
        return new int[]{10, 20, 30};
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
