public class Validated {
    private final int value;

    public Validated() {
        this(0);
    }

    public Validated(int value) {
        if (value < 0) {
            this.value = 0;
            return;
        }
        this.value = value;
    }

    public int value() {
        return value;
    }
}
