# Aggressive inline: call as array dimension


### Input:
```java
public class Foo {
    public int run() {
        int[] arr = new int[compute()];
        return arr.length;
    }

    private int compute() {
        int n = 3;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 29  
array dimension; body hoisted before enclosing statement, dimension replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			int n = 3;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		int[] arr = new int[__inline_result];
        return arr.length;
    }

    private int compute() {
        int n = 3;
        return n;
    }
}
```