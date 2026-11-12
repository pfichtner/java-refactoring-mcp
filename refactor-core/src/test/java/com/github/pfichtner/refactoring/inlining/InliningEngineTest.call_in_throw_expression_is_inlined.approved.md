# Aggressive inline: call in throw expression


### Input:
```java
public class Foo {
    public int run() {
        try {
            throw compute();
        } catch (IllegalArgumentException e) {
            return e.getMessage().length();
        }
    }

    private IllegalArgumentException compute() {
        String msg = "error";
        return new IllegalArgumentException(msg);
    }
}
```

### Refactoring:
**inline method** `compute()` at line 4, col 19  
throw expression; body hoisted before throw statement, expression replaced with result

### Output:
```java
public class Foo {
    public int run() {
        try {
            IllegalArgumentException __inline_result;
			__inline_label: {
				String msg = "error";
				{
					__inline_result = new IllegalArgumentException(msg);
					break __inline_label;
				}
			}
			throw __inline_result;
        } catch (IllegalArgumentException e) {
            return e.getMessage().length();
        }
    }

    private IllegalArgumentException compute() {
        String msg = "error";
        return new IllegalArgumentException(msg);
    }
}
```