# Aggressive inline: delegating constructor with early return


### Input:
```java
public class Validated {
    private final int value;

    public Validated() {
        this(0);
    }

    public Validated(int value) {
        if (value < 0) {
            this.value = 0;
            return;
        }
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

### Refactoring:
**inline constructor** `this(0)` at line 5, col 9  
early return rewritten to labeled break; this() call replaced by target body

### Output:
```java
public class Validated {
    private final int value;

    public Validated() {
        int __inline_value = 0;
		__inline_label: {
			if (__inline_value < 0) {
				this.value = 0;
				break __inline_label;
			}
			this.value = __inline_value;
		}
    }

    public Validated(int value) {
        if (value < 0) {
            this.value = 0;
            return;
        }
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```