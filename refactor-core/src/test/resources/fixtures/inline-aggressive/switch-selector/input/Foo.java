public class Foo {
    public int run() {
        switch (compute()) {
            case 1: return 10;
            case 2: return 20;
            default: return 0;
        }
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
