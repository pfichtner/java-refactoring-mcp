# Aggressive inline — rejected: checked exception


### Input:
```java
import java.io.IOException;

public class Foo {
    public String run() throws Exception {
        return read();
    }

    private String read() throws IOException {
        return "value";
    }
}
```

### Refactoring:
**inline method** `read()` at line 5, col 16  
inlining would move checked-exception handling to the call site

### Diagnostic:
```
method declares checked exception java.io.IOException; inlining could change checked-exception handling at the call site
```