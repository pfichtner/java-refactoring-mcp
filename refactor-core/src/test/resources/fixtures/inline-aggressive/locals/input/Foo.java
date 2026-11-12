public class Foo {
    public int compute(int input) {
        return adjust(input);
    }

    private int adjust(int value) {
        int shifted = value + 1;
        int doubled = shifted * 2;
        return doubled;
    }
}
