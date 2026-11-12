# Aggressive inline: pure argument used twice


### Input:
```java
public class Foo {
    public int run() {
        int value = 5;
        return twice(value);
    }

    private int twice(int value) {
        return value + value;
    }
}
```

### Refactoring:
**inline method** `twice(value)` at line 4, col 16  
the pure local argument is read twice, so it stays captured in a temporary

### Output:
```java
public class Foo {
    public int run() {
        int value = 5;
        int __inline_value = value;
		int __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_value + __inline_value;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int twice(int value) {
        return value + value;
    }
}
```