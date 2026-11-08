import java.util.function.IntSupplier;

public class Foo {
    public int run() {
        int value = 5;
        return plusOneLater(value);
    }

    private int plusOneLater(int value) {
        IntSupplier supplier = () -> value + 1;
        return supplier.getAsInt();
    }
}
