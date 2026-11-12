class Base {
    private int secret = 1;

    final int read() {
        return secret;
    }
}

public class Foo {
    public int run(Base other) {
        return other.read();
    }
}
