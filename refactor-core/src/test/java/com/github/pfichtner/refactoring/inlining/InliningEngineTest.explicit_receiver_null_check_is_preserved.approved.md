# Aggressive inline: explicit receiver null check


### Input:
```java
public class Foo {
    private int value = 1;

    public boolean run() {
        Foo other = null;
        try {
            other.read();
            return true;
        } catch (NullPointerException expected) {
            return false;
        }
    }

    private int read() {
        return value;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 7, col 13  
the captured receiver gets an explicit null check before the body runs

### Output:
```java
public class Foo {
    private int value = 1;

    public boolean run() {
        Foo other = null;
        try {
            {
				Foo __inline_receiver = other;
				java.util.Objects.requireNonNull(__inline_receiver);
				int __inline_result;
				__inline_label: {
					{
						__inline_result = __inline_receiver.value;
						break __inline_label;
					}
				}
			}
            return true;
        } catch (NullPointerException expected) {
            return false;
        }
    }

    private int read() {
        return value;
    }
}
```