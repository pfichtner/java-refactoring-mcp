public class Foo {
    public int run() {
        int[] arr = {10, 20, 30};
        return arr[compute()];
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
