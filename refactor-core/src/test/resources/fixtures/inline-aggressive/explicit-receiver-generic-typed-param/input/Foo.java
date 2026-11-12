class Box<T> {
    T item;
    final void set(T value) {
        this.item = value;
    }
}

public class Foo {
    public void run(Box<String> box, String value) {
        box.set(value);
    }
}
