public class Foo {
    public int run() {
        int sum = 0;
        for (int x : compute()) {
            sum += x;
        }
        return sum;
    }

    private int[] compute() {
        int[] arr = {1, 2, 3};
        return arr;
    }
}
