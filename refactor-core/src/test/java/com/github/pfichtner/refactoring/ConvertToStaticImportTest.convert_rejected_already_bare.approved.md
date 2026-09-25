# Convert to static import — rejected: call already bare


### Input:
```java
import java.util.List;
import static java.util.stream.Collectors.joining;

public class Foo {
    public String join(List<String> words) {
        return words.stream().collect(joining(", "));
    }
}
```

### Refactoring:
**convert to static import** `joining` at line 6, col 39  
call is already unqualified — nothing to do

### Diagnostic:
```
The call 'joining' is already a bare (unqualified) call — no qualifier to remove.
```