class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class Foo {
    public Object run(Box<?> box) {
        return box.get();
    }
}
