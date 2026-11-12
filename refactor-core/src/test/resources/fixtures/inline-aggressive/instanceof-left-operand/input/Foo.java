public class Foo {
    public boolean run() {
        return compute() instanceof String;
    }

    private Object compute() {
        Object o = "hello";
        return o;
    }
}
