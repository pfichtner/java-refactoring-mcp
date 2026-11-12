# Aggressive inline: cross-type generic explicit receiver with parameterized type argument


### Input:
```java
import java.util.List;

class Box<T> {
    T item;
    final T copy() {
        T result = item;
        return result;
    }
}

public class Foo {
    public List<String> run(Box<List<String>> box) {
        return box.copy();
    }
}
```

### Refactoring:
**inline method** `box.copy()` at line 13, col 16  
type variable T substituted with List<String>; body type references structurally replaced

### Output:
```java
import java.util.List;

class Box<T> {
    T item;
    final T copy() {
        T result = item;
        return result;
    }
}

public class Foo {
    public List<String> run(Box<List<String>> box) {
        Box<List<String>> __inline_receiver = box;
		java.util.Objects.requireNonNull(__inline_receiver);
		List<String> __inline_result;
		__inline_label: {
			List<String> result = __inline_receiver.item;
			{
				__inline_result = result;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```