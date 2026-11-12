# Aggressive inline: delegation chain fully resolved (inlineAll)


### Input:
```java
class Foo {
    private int twice(int n) {
        return n * 2;
    }

    private int doubled(int n) {
        return twice(n);
    }

    int nonInlineable(int x) {
        return x + 10;
    }

    int compute(int n) {
        return nonInlineable(n) + doubled(n);
    }
}
```

### Refactoring:
**inline all** `doubled` → delegation substitution; `twice` → getter substitution  
two passes collapse the delegation chain: delegation replaces `doubled(n)` with `twice(n)`, then getter inlining replaces `twice(n)` with `n * 2`

### Output:
```java
class Foo {
    private int twice(int n) {
        return n * 2;
    }

    private int doubled(int n) {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = n * 2;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    int nonInlineable(int x) {
        return x + 10;
    }

    int compute(int n) {
        return nonInlineable(n) + n * 2;
    }
}
```