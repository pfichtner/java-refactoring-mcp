public class Foo {
    public int run() {
        int sum = 0;
        for (int i = 0, end = compute(); i < end; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
