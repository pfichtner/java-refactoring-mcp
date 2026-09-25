import java.util.List;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(joining(", "));
    }
}
