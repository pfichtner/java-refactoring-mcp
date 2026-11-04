# Aggressive inline: finally runs before return


### Input:
```java
public class Foo {
    private int log;

    public int run() {
        int result = choose();
        return result * 10 + log;
    }

    private int choose() {
        int value = 0;
        try {
            value = 1;
            return value;
        } finally {
            log = value + 1;
        }
    }
}
```

### Refactoring:
**inline method** `choose()` at line 5, col 22  
return becomes an assignment plus a labeled break; the finally block still runs

### Output:
```java
public class Foo {
    private int log;

    public int run() {
        int __inline_result;
		__inline_label: {
			int value = 0;
			try {
				value = 1;
				{
					__inline_result = value;
					break __inline_label;
				}
			} finally {
				log = value + 1;
			}
		}
		int result = __inline_result;
        return result * 10 + log;
    }

    private int choose() {
        int value = 0;
        try {
            value = 1;
            return value;
        } finally {
            log = value + 1;
        }
    }
}
```