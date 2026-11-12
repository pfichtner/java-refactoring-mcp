public class Range {
    private final int start;
    private final int end;

    public Range(int start) {
        this(start, start + 10);
    }

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int length() {
        return end - start;
    }
}
