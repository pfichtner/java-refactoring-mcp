# Inline constant: MAX — all occurrences, declaration removed


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
**inline constant** `MAX` — all occurrences, declaration removed  
both references replaced with 100; MAX field declaration deleted

### Output:
```java
public class Foo {

    public boolean isValid(int x) {
        return x <= 100;
    }

    public int clamp(int x) {
        return Math.min(x, 100);
    }
}
```