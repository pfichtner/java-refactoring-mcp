# Aggressive inline: call in if-condition


### Input:
```java
public class Foo {
    public int run() {
        if (compute()) {
            return 1;
        }
        return 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 13  
if-condition position; body hoisted before if-statement, condition replaced with result

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
		if (__inline_result) {
            return 1;
        }
        return 0;
    }

    private boolean compute() {
        int x = 1;
        return x > 0;
    }
}
```