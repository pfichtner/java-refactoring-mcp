# convert-to-static-import


### Command:
```
convert-to-static-import --file {root}/Foo.java --line 6 --column 50
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(joining(", "));
    }
}
```