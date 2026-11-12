# Aggressive inline: return in finally overrides


### Input:
```java
public class Foo {
    public int run() {
        return choose();
    }

    private int choose() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }
}
```

### Refactoring:
**inline method** `choose()` at line 3, col 16  
both returns are rewritten; the finally assignment runs last

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			try {
				{
					__inline_result = 1;
					break __inline_label;
				}
			} finally {
				{
					__inline_result = 2;
					break __inline_label;
				}
			}
		}
		return __inline_result;
    }

    private int choose() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }
}
```