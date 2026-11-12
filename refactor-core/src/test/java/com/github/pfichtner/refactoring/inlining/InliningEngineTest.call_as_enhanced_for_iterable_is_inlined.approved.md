# Aggressive inline: call as enhanced-for iterable


### Input:
```java
public class Foo {
    public int run() {
        int sum = 0;
        for (int x : compute()) {
            sum += x;
        }
        return sum;
    }

    private int[] compute() {
        int[] arr = {1, 2, 3};
        return arr;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 4, col 22  
enhanced-for iterable; body hoisted before for statement, iterable replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int sum = 0;
        int[] __inline_result;
		__inline_label: {
			int[] arr = { 1, 2, 3 };
			{
				__inline_result = arr;
				break __inline_label;
			}
		}
		for (int x : __inline_result) {
            sum += x;
        }
        return sum;
    }

    private int[] compute() {
        int[] arr = {1, 2, 3};
        return arr;
    }
}
```