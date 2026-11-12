# Aggressive inline: delegating constructor


### Input:
```java
public class Counter {
    private int count;

    public Counter() {
        this(0);
    }

    public Counter(int initial) {
        this.count = initial;
    }

    public int value() {
        return count;
    }
}
```

### Refactoring:
**inline constructor** `this(0)` at line 5, col 9  
this() call replaced by target constructor body

### Output:
```java
public class Counter {
    private int count;

    public Counter() {
        this.count = 0;
    }

    public Counter(int initial) {
        this.count = initial;
    }

    public int value() {
        return count;
    }
}
```