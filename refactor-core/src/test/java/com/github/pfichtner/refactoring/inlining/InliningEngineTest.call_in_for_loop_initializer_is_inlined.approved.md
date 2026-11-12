# Aggressive inline: call in for-loop initializer


### Input:
```java
public class Foo {
    public int run() {
        int sum = 0;
        for (int i = compute(); i < 5; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 4, col 22  
for-loop initializer; body hoisted before for-statement, initializer replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int sum = 0;
        int __inline_result;
		__inline_label: {
			int n = 2;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		for (int i = __inline_result; i < 5; i++) {
            sum += i;
        }
        return sum;
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
```