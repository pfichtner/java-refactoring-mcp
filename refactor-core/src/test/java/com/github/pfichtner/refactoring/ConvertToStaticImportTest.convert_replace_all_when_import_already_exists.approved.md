# Convert to static import: import already present, replace all — no duplicate import


### Input:
```java
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
```

### Refactoring:
**convert to static import** `Collectors.joining` — import already exists, replace all  
qualifier removed from all calls; existing import kept as-is

### Output:
```java
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String joinComma(List<String> words) {
        return words.stream().collect(joining(", "));
    }
    public String joinDash(List<String> words) {
        return words.stream().collect(joining("-"));
    }
}
```