public class Mutual {
    public int start() {
        return first(3);
    }

    private int first(int value) {
        if (value <= 0) {
            return 0;
        }
        return second(value - 1);
    }

    private int second(int value) {
        if (value <= 0) {
            return 0;
        }
        return first(value - 1);
    }
}
