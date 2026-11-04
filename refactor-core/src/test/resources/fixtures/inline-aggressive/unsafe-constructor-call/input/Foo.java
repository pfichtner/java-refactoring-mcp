public class Foo {
    public int run() {
        return outer(new Foo());
    }

    private int outer(Foo value) {
        return 1;
    }
}
