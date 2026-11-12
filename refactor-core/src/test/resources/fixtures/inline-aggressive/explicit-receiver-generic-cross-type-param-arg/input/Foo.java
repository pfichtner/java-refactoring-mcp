import java.util.List;

class Box<T> {
    T item;
    final T copy() {
        T result = item;
        return result;
    }
}

public class Foo {
    public List<String> run(Box<List<String>> box) {
        return box.copy();
    }
}
