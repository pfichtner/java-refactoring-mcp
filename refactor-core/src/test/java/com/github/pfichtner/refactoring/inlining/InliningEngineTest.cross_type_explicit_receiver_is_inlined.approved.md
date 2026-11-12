# Aggressive inline: cross-type explicit receiver


### Input:
```java
class Base {
    final int read() {
        return 1;
    }
}

public class Foo extends Base {
    public int run(Base other) {
        return other.read();
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 9, col 16  
receiver captured once; body from non-subtype callee inlined with receiver substitution

### Output:
```java
class Base {
    final int read() {
        return 1;
    }
}

public class Foo extends Base {
    public int run(Base other) {
        Base __inline_receiver = other;
		java.util.Objects.requireNonNull(__inline_receiver);
		int __inline_result;
		__inline_label: {
			{
				__inline_result = 1;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```