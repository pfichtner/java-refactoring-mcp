# Aggressive inline: constant inlined after method inline


### Input:
```java
public class Foo {
    private static final int LIMIT = 50;

    public int run() {
        return compute();
    }

    private int compute() {
        return LIMIT * 2;
    }
}
```

### Refactoring:
**inline all** `compute`, then `LIMIT` twice  
method inlined first; constant then replaced in run() and compute()

### Output:
```java
public class Foo {
    private static final int LIMIT = 50;

    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 50 * 2;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int compute() {
        return 50 * 2;
    }
}
```