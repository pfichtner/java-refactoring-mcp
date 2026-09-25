# Convert to static import: Collectors.joining — single occurrence


### Input:
```java
import java.util.List;
import java.util.stream.Collectors;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(Collectors.joining(", "));
    }
}
```

### Refactoring:
**convert to static import** `Collectors.joining` at line 6, col 50  
import added; qualifier removed from one call

### Output:
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