# Aggressive inline: iterative chain until fixed point


### Input:
```java
public class Chain {
    public int start() {
        return first(1);
    }

    private int first(int x) {
        return second(x + 1);
    }

    private int second(int x) {
        return third(x * 2);
    }

    private int third(int x) {
        return x - 3;
    }
}
```

### Refactoring:
**inline all** `first`, `second`, `third`  
three passes; each pass re-parses the rewritten source

### Output:
```java
public class Chain {
    public int start() {
        int __inline_result2;
		__inline_label2: {
			int __inline_x1 = 1 + 1;
			int __inline_result1;
			__inline_label1: {
				int __inline_x = __inline_x1 * 2;
				int __inline_result;
				__inline_label: {
					{
						__inline_result = __inline_x - 3;
						break __inline_label;
					}
				}
				{
					__inline_result1 = __inline_result;
					break __inline_label1;
				}
			}
			{
				__inline_result2 = __inline_result1;
				break __inline_label2;
			}
		}
		return __inline_result2;
    }

    private int first(int x) {
        int __inline_x1 = x + 1;
		int __inline_result1;
		__inline_label1: {
			int __inline_x = __inline_x1 * 2;
			int __inline_result;
			__inline_label: {
				{
					__inline_result = __inline_x - 3;
					break __inline_label;
				}
			}
			{
				__inline_result1 = __inline_result;
				break __inline_label1;
			}
		}
		return __inline_result1;
    }

    private int second(int x) {
        int __inline_x = x * 2;
		int __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_x - 3;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int third(int x) {
        return x - 3;
    }
}
```