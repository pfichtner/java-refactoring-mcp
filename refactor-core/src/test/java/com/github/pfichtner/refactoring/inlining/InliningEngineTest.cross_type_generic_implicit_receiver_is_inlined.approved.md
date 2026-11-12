# Aggressive inline: cross-type generic implicit receiver


### Input:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class StringBox extends Box<String> {
    public String run() {
        return get();
    }
}
```

### Refactoring:
**inline method** `get()` at line 10, col 16  
inherited method from generic supertype; type variable T substituted with String

### Output:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class StringBox extends Box<String> {
    public String run() {
        String __inline_result;
		__inline_label: {
			{
				__inline_result = item;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```