# Aggressive inline: multiple return statements


### Input:
```java
public class Foo {
    public int classify(int n) {
        return sign(n);
    }

    private int sign(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 0;
        }
        return 1;
    }
}
```

### Refactoring:
**inline method** `sign(n)` at line 3, col 16  
each return becomes an assignment plus a break out of the labeled block

### Output:
```java
public class Foo {
    public int classify(int n) {
        int __inline_value = n;
		int __inline_result;
		__inline_label: {
			if (__inline_value < 0) {
				{
					__inline_result = -1;
					break __inline_label;
				}
			}
			if (__inline_value == 0) {
				{
					__inline_result = 0;
					break __inline_label;
				}
			}
			{
				__inline_result = 1;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int sign(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 0;
        }
        return 1;
    }
}
```