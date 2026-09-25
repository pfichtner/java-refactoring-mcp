import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String joinComma(List<String> words) {
        return words.stream().collect(Collectors.joining(", "));
    }
    public String joinDash(List<String> words) {
        return words.stream().collect(Collectors.joining("-"));
    }
}
