public class Foo extends Base {
    public int run() {
        return super.read();
    }
}

class Base {
    int read() {
        return 1;
    }
}
