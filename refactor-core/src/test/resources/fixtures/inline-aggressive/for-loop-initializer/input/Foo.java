public class Foo {
    public int run() {
        int sum = 0;
        for (int i = compute(); i < 5; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
