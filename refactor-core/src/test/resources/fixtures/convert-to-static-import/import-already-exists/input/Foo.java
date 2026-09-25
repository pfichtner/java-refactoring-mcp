import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(Collectors.joining(", "));
    }
}
