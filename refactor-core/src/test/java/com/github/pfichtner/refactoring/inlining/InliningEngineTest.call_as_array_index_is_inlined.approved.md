# Aggressive inline: call as array index


### Input:
```java
public class Foo {
    public int run() {
        int[] arr = {10, 20, 30};
        return arr[compute()];
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 4, col 20  
array index; body hoisted before enclosing statement, index replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int[] arr = {10, 20, 30};
        int __inline_result;
		__inline_label: {
			int n = 2;
			{
				__inline_result = n;
				break __inline_label;
			}
		}
		return arr[__inline_result];
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
```