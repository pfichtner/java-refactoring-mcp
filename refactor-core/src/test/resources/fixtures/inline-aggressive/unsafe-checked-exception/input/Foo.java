import java.io.IOException;

public class Foo {
    public String run() throws Exception {
        return read();
    }

    private String read() throws IOException {
        return "value";
    }
}
