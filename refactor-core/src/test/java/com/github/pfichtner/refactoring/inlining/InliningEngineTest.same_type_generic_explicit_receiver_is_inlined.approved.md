# Aggressive inline: same-type generic explicit receiver


### Input:
```java
public class Foo<T> {
    private int value = 1;

    public int run(Foo<T> other) {
        return other.read();
    }

    private int read() {
        return value;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 5, col 16  
type variable T is in scope for same-type caller; receiver captured once

### Output:
```java
public class Foo<T> {
    private int value = 1;

    public int run(Foo<T> other) {
        Foo __inline_receiver = other;
		java.util.Objects.requireNonNull(__inline_receiver);
		int __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_receiver.value;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int read() {
        return value;
    }
}
```