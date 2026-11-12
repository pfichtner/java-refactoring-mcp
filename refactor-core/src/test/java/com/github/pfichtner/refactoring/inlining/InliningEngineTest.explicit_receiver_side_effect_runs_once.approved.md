# Aggressive inline: explicit receiver side effect once


### Input:
```java
public class Foo {
    private int value = 1;
    private int calls;

    public int run() {
        Foo other = next();
        other.read();
        return calls;
    }

    private Foo next() {
        calls++;
        return this;
    }

    private int read() {
        return value + value;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 7, col 9  
receiver captured once; the body uses it twice

### Output:
```java
public class Foo {
    private int value = 1;
    private int calls;

    public int run() {
        Foo other = next();
        {
			Foo __inline_receiver = other;
			java.util.Objects.requireNonNull(__inline_receiver);
			int __inline_result;
			__inline_label: {
				{
					__inline_result = __inline_receiver.value + __inline_receiver.value;
					break __inline_label;
				}
			}
		}
        return calls;
    }

    private Foo next() {
        calls++;
        return this;
    }

    private int read() {
        return value + value;
    }
}
```