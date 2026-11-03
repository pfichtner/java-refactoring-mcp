public class SideEffect {
    private int calls;

    public int run() {
        return twice(increment());
    }

    private int increment() {
        calls++;
        return calls;
    }

    private int twice(int value) {
        return value + value;
    }
}
