# Aggressive inline: nested expression in return


### Input:
```java
public class Foo {
    public int run() {
        return 1 + compute();
    }

    private int compute() {
        return 2;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 20  
pure literal `1` before the call; hoisting does not reorder any observable effect

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 2;
				break __inline_label;
			}
		}
		return 1 + __inline_result;
    }

    private int compute() {
        return 2;
    }
}
```