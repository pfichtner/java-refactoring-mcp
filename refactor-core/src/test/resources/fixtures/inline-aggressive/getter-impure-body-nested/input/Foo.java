public class Foo {
    int value = 0;

    public int run() {
        return sideEffect() + compute();
    }

    private int sideEffect() {
        return value++;
    }

    private int compute() {
        int x = this.value;
        return x + 1;
    }
}
