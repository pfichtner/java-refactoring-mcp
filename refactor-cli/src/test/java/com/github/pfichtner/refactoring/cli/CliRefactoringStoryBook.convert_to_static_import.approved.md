# convert-to-static-import


### Command:
```
convert-to-static-import --file {root}/Foo.java --line 6 --column 50 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Foo.java ===
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(joining(", "));
    }
}
```