# Convert to static import: import already present — qualifier removed, no duplicate import


### Input:
```java
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(Collectors.joining(", "));
    }
}
```

### Refactoring:
**convert to static import** `Collectors.joining` — import already exists  
qualifier removed; existing import kept as-is

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