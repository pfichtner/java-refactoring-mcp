# Aggressive inline: call in for-loop multi-fragment VDE


### Input:
```java
public class Foo {
    public int run() {
        int sum = 0;
        for (int i = 0, end = compute(); i < end; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 4, col 31  
for-loop multi-fragment VDE; body hoisted before for-statement, initializer replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int sum = 0;
        int __inline_result;
		__inline_label: {
			int n = 5;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		for (int i = 0, end = __inline_result; i < end; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
```