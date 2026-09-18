public class Foo {
    public int run() {
        int x = 3;
        int y = 4;
        int result = add(x, y);
        return result;
    }

    private int add(int a, int b) {
        return a + b;
    }
}
