# Aggressive inline: maxTransformations budget stops the chain


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
one transformation applied, then the maxTransformations budget stops the run

### Output:
```java
public class Chain {
    public int start() {
        return first(1);
    }

    private int first(int x) {
        return second(x + 1);
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