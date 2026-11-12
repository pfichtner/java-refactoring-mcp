class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class StringBox extends Box<String> {
    public String run() {
        return get();
    }
}
