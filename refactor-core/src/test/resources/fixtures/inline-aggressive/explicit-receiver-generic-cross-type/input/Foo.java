class Box<T> {
    final int size() {
        return 1;
    }
}

public class Foo {
    public int run(Box<String> box) {
        return box.size();
    }
}
