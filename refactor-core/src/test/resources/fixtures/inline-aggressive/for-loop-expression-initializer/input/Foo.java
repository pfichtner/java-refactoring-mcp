public class Foo {
    public int run() {
        int i = 0;
        int sum = 0;
        for (i = compute(); i < 5; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
