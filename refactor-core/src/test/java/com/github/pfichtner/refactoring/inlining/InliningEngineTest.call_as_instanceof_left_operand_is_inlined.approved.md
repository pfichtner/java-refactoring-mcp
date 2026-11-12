# Aggressive inline: call as instanceof left operand


### Input:
```java
public class Foo {
    public boolean run() {
        return compute() instanceof String;
    }

    private Object compute() {
        Object o = "hello";
        return o;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 16  
instanceof left operand; body hoisted before enclosing statement, operand replaced with result

### Output:
```java
public class Foo {
    public boolean run() {
        Object __inline_result;
		__inline_label: {
			Object o = "hello";
			{
				__inline_result = o;
				break __inline_label;
			}
		}
		return __inline_result instanceof String;
    }

    private Object compute() {
        Object o = "hello";
        return o;
    }
}
```