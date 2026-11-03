# Aggressive inline: method with local variables


### Input:
```java
public class Foo {
    public int compute(int input) {
        return adjust(input);
    }

    private int adjust(int value) {
        int shifted = value + 1;
        int doubled = shifted * 2;
        return doubled;
    }
}
```

### Refactoring:
**inline method** `adjust(input)` at line 3, col 16  
body locals are copied and kept when they do not collide with caller names

### Output:
```java
public class Foo {
    public int compute(int input) {
        int __inline_value = input;
		int __inline_result;
		__inline_label: {
			int shifted = __inline_value + 1;
			int doubled = shifted * 2;
			{
				__inline_result = doubled;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int adjust(int value) {
        int shifted = value + 1;
        int doubled = shifted * 2;
        return doubled;
    }
}
```