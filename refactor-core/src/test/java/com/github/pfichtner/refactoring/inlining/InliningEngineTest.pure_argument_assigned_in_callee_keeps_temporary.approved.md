# Aggressive inline: argument assigned inside callee


### Input:
```java
public class Foo {
    public int run() {
        int value = 1;
        int result = bump(value);
        return value * 100 + result;
    }

    private int bump(int value) {
        return ++value;
    }
}
```

### Refactoring:
**inline method** `bump(value)` at line 4, col 22  
the callee increments its parameter, so the argument stays a captured temporary

### Output:
```java
public class Foo {
    public int run() {
        int value = 1;
        int __inline_value = value;
		int __inline_result;
		__inline_label: {
			{
				__inline_result = ++__inline_value;
				break __inline_label;
			}
		}
		int result = __inline_result;
        return value * 100 + result;
    }

    private int bump(int value) {
        return ++value;
    }
}
```