public class Foo {
    public int classify(int n) {
        return sign(n);
    }

    private int sign(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 0;
        }
        return 1;
    }
}
