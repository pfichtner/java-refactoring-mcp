# Aggressive inline: argument side effects evaluated once


### Input:
```java
public class SideEffect {
    private int calls;

    public int run() {
        return twice(increment());
    }

    private int increment() {
        calls++;
        return calls;
    }

    private int twice(int value) {
        return value + value;
    }
}
```

### Refactoring:
**inline method** `twice(increment())` at line 5, col 16  
the side-effecting argument is bound once; the parameter is used twice

### Output:
```java
public class SideEffect {
    private int calls;

    public int run() {
        int __inline_value = increment();
		int __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_value + __inline_value;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int increment() {
        calls++;
        return calls;
    }

    private int twice(int value) {
        return value + value;
    }
}
```