public class Shadow {
    private int x = 10;

    public int compute() {
        int x = 20; // shadows the field
        return x + 1;
    }
}
