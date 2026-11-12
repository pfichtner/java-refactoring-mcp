class Base {
    final int read() {
        return 1;
    }
}

public class Foo extends Base {
    public int run(Base other) {
        return other.read();
    }
}
