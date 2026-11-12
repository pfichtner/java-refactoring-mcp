# Aggressive inline: generated labels avoid existing names


### Input:
```java
public class Foo {
    public int run() {
        int n = 5;
        __inline_label: {
            if (n > 0) {
                return twice(n);
            }
        }
        return 0;
    }

    private int twice(int value) {
        int __inline_result = value;
        __inline_label: {
            if (__inline_result > 10) {
                return __inline_result;
            }
        }
        return __inline_result * 2;
    }
}
```

### Refactoring:
**inline method** `twice(n)` at line 6, col 24  
existing __inline_* labels and locals force suffixed generated names

### Output:
```java
public class Foo {
    public int run() {
        int n = 5;
        __inline_label: {
            if (n > 0) {
                int __inline_result1;
				__inline_label1: {
					int __inline_result = n;
					__inline_label___inline_label: {
						if (__inline_result > 10) {
							{
								__inline_result1 = __inline_result;
								break __inline_label1;
							}
						}
					}
					{
						__inline_result1 = __inline_result * 2;
						break __inline_label1;
					}
				}
				return __inline_result1;
            }
        }
        return 0;
    }

    private int twice(int value) {
        int __inline_result = value;
        __inline_label: {
            if (__inline_result > 10) {
                return __inline_result;
            }
        }
        return __inline_result * 2;
    }
}
```