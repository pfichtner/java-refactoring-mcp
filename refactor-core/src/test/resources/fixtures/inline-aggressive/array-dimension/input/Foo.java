public class Foo {
    public int run() {
        int[] arr = new int[compute()];
        return arr.length;
    }

    private int compute() {
        int n = 3;
        return n;
    }
}
