# Convert to static import — rejected: name clash with existing static import


### Input:
```java
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static java.util.Arrays.sort;

public class Foo {
    public void process(List<Integer> data, int[] arr) {
        sort(arr);
        Collections.sort(data);
    }
}
```

### Refactoring:
**convert to static import** `Collections.sort` at line 9, col 21  
'sort' already statically imported from java.util.Arrays

### Diagnostic:
```
Cannot add 'import static java.util.Collections.sort': method name 'sort' is already statically imported from 'java.util.Arrays'. Use the qualified form instead.
```