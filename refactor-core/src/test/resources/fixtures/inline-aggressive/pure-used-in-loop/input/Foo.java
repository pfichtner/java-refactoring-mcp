public class Foo {
    public int run() {
        int limit = 4;
        return sumBelow(limit);
    }

    private int sumBelow(int limit) {
        int total = 0;
        for (int i = 0; i < limit; i++) {
            total += i;
        }
        return total;
    }
}
