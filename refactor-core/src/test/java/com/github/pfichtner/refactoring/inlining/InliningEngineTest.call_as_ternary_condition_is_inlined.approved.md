# Aggressive inline: call as ternary condition


### Input:
```java
public class Foo {
    public int run() {
        return compute() ? 1 : 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 16  
ternary condition always evaluated; body hoisted before enclosing statement

### Output:
```java
public class Foo {
    public int run() {
        boolean __inline_result;
		__inline_label: {
			int x = 1;
			{
				__inline_result = x > 0;
				break __inline_label;
			}
		}
		return __inline_result ? 1 : 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
```