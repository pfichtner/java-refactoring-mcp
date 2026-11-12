# Aggressive inline: call in for-loop expression initializer


### Input:
```java
public class Foo {
    public int run() {
        int i = 0;
        int sum = 0;
        for (i = compute(); i < 5; i++) {
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
**inline method** `compute()` at line 5, col 18  
for-loop expression initializer; body hoisted before for-statement, initializer replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int i = 0;
        int sum = 0;
        int __inline_result;
		__inline_label: {
			int n = 2;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		for (i = __inline_result; i < 5; i++) {
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