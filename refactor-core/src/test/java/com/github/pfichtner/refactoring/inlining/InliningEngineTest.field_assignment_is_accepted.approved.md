# Aggressive inline: field assignment


### Input:
```java
public class Foo {
    private int value;

    public int run() {
        value = compute();
        return value;
    }

    private int compute() {
        return 7;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 5, col 17  
assignment right-hand side with a side-effect-free `this.field` left-hand side

### Output:
```java
public class Foo {
    private int value;

    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 7;
				break __inline_label;
			}
		}
		value = __inline_result;
        return value;
    }

    private int compute() {
        return 7;
    }
}
```
