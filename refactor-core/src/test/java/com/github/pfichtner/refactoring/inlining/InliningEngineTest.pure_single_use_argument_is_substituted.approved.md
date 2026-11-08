# Aggressive inline: pure single-use argument substituted


### Input:
```java
public class Foo {
    public int run() {
        int value = 21;
        return plusOne(value);
    }

    private int plusOne(int value) {
        return value + 1;
    }
}
```

### Refactoring:
**inline method** `plusOne(value)` at line 4, col 16  
the pure local argument is copied to its single use; no argument temporary

### Output:
```java
public class Foo {
    public int run() {
        int value = 21;
        int __inline_result;
		__inline_label: {
			{
				__inline_result = value + 1;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int plusOne(int value) {
        return value + 1;
    }
}
```