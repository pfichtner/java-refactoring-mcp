# Inline constant: MAX — this occurrence only


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
**inline constant** `MAX` at line 5, col 21  
only the first reference replaced; declaration and other uses unchanged

### Output:
```java
public class Foo {
    private static final int MAX = 100;

    public boolean isValid(int x) {
        return x <= 100;
    }

    public int clamp(int x) {
        return Math.min(x, MAX);
    }
}
```