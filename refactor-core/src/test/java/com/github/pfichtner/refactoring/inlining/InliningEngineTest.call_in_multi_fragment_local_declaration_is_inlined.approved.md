# Aggressive inline: call in multi-fragment local declaration


### Input:
```java
public class Foo {
    public int run() {
        int a = 0, b = compute();
        return a + b;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 24  
multi-fragment local declaration; body hoisted before declaration, initializer replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			int n = 5;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		int a = 0, b = __inline_result;
        return a + b;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
```