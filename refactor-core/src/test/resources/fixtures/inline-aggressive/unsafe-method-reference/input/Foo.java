import java.util.function.Supplier;

public class Foo {
    public int run() {
        return outer(this::value);
    }

    private int outer(Supplier<Integer> supplier) {
        return supplier.get();
    }

    private int value() {
        return 1;
    }
}
