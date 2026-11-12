# Aggressive inline: explicit receiver captured


### Input:
```java
public class Foo {
    private int value = 1;

    public int run() {
        Foo other = new Foo();
        other.value = 7;
        return other.read();
    }

    private int read() {
        return helper() + value;
    }

    private int helper() {
        return 2;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 7, col 16  
the receiver is bound once and 'this' plus implicit instance members are rewritten

### Output:
```java
public class Foo {
    private int value = 1;

    public int run() {
        Foo other = new Foo();
        other.value = 7;
        Foo __inline_receiver = other;
		java.util.Objects.requireNonNull(__inline_receiver);
		int __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_receiver.helper() + __inline_receiver.value;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int read() {
        return helper() + value;
    }

    private int helper() {
        return 2;
    }
}
```