# Aggressive inline all: delegating constructor


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
**inline all** `Counter()` delegating constructor  
this() call replaced by target constructor body; fixed point reached

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