# Aggressive inline: type-qualified static call


### Input:
```java
public class Foo {
    public int run() {
        return Foo.compute(2);
    }

    private static int compute(int value) {
        return value * 3;
    }
}
```

### Refactoring:
**inline method** `Foo.compute(2)` at line 3, col 16  
static method called through its type; same-type class initialization is unchanged

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 2 * 3;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private static int compute(int value) {
        return value * 3;
    }
}
```