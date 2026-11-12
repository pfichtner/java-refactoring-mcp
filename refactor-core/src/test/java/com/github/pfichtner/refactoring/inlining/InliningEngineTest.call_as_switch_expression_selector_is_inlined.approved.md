# Aggressive inline: call as switch expression selector


### Input:
```java
public class Foo {
    public int run() {
        int result = switch (compute()) {
            case 1 -> 10;
            case 2 -> 20;
            default -> 0;
        };
        return result;
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 30  
switch expression selector; body hoisted before enclosing statement, selector replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			int x = 1;
			{
				__inline_result = x;
				break __inline_label;
			}
		}
		int result = switch (__inline_result) {
            case 1 -> 10;
            case 2 -> 20;
            default -> 0;
        };
        return result;
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
```