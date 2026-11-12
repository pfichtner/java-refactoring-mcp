# Aggressive inline: delegating constructor with params


### Input:
```java
public class Range {
    private final int start;
    private final int end;

    public Range(int start) {
        this(start, start + 10);
    }

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int length() {
        return end - start;
    }
}
```

### Refactoring:
**inline constructor** `this(start, start + 10)` at line 6, col 9  
parameters bound to temporaries; this() call replaced by target body

### Output:
```java
public class Range {
    private final int start;
    private final int end;

    public Range(int start) {
        int __inline_end = start + 10;
		this.start = start;
		this.end = __inline_end;
    }

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int length() {
        return end - start;
    }
}
```