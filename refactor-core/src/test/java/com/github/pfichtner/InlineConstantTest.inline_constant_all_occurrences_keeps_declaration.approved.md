# Inline constant: MAX — all occurrences, declaration kept


### Input:
```java
public class Foo {
    private static final int MAX = 100;

    public boolean isValid(int x) {
        return x <= MAX;
    }

    public int clamp(int x) {
        return Math.min(x, MAX);
    }
}
```

### Refactoring:
**inline constant** `MAX` — all occurrences, declaration kept  
both references replaced with 100; MAX declaration stays

### Output:
```java
public class Foo {
    private static final int MAX = 100;

    public boolean isValid(int x) {
        return x <= 100;
    }

    public int clamp(int x) {
        return Math.min(x, 100);
    }
}
```