# Convert to static import: Collectors.joining — all occurrences


### Input:
```java
import java.util.List;
import java.util.stream.Collectors;

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
**convert to static import** `Collectors.joining` — replace all  
import added once; qualifier removed from both calls

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