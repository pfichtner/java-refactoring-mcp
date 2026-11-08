# Aggressive inline: simple private method


### Input:
```java
public class Foo {
    public int compute() {
        return twice(21);
    }

    private int twice(int value) {
        return value * 2;
    }
}
```

### Refactoring:
**inline method** `twice(21)` at line 3, col 16  
argument bound to a temporary; return value hoisted into a result temporary

### Output:
```java
public class Foo {
    public int compute() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 21 * 2;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int twice(int value) {
        return value * 2;
    }
}
```