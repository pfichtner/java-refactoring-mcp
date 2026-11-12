class Foo {
    private int twice(int n) {
        return n * 2;
    }

    private int doubled(int n) {
        return twice(n);
    }

    int nonInlineable(int x) {
        return x + 10;
    }

    int compute(int n) {
        return nonInlineable(n) + doubled(n);
    }
}
