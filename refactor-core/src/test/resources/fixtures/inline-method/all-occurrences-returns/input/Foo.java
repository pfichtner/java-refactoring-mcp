public class Foo {
    public void run() {
        int x = 3;
        int y = 4;
        int a = add(x, y);
        int b = add(1, 2);
    }

    private int add(int p, int q) {
        return p + q;
    }
}
