# Aggressive inline: argument used in a loop


### Input:
```java
public class Foo {
    public int run() {
        int limit = 4;
        return sumBelow(limit);
    }

    private int sumBelow(int limit) {
        int total = 0;
        for (int i = 0; i < limit; i++) {
            total += i;
        }
        return total;
    }
}
```

### Refactoring:
**inline method** `sumBelow(limit)` at line 4, col 16  
the single use sits in a loop, so the argument stays a captured temporary

### Output:
```java
public class Foo {
    public int run() {
        int limit = 4;
        int __inline_limit = limit;
		int __inline_result;
		__inline_label: {
			int total = 0;
			for (int i = 0; i < __inline_limit; i++) {
				total += i;
			}
			{
				__inline_result = total;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int sumBelow(int limit) {
        int total = 0;
        for (int i = 0; i < limit; i++) {
            total += i;
        }
        return total;
    }
}
```