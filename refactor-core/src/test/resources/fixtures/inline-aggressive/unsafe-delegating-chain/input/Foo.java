public class Foo {
    private final int value;

    public Foo() {
        this(42);
    }

    public Foo(int value) {
        this(value, true);
    }

    public Foo(int value, boolean valid) {
        this.value = valid ? value : 0;
    }
}
