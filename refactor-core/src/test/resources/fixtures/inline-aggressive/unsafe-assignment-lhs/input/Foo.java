public class Foo {
    private int[] values = new int[3];

    public void run() {
        values[index()] = compute();
    }

    private int index() {
        return 0;
    }

    private int compute() {
        return 1;
    }
}
