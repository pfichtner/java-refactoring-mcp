public class Foo {
    public int run() {
        int result = switch (compute()) {
            case 1 -> 10;
            case 2 -> 20;
            default -> 0;
        };
        return result;
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
