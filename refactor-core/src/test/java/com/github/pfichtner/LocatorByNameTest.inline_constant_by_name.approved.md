# Inline constant by name: MAX


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
**inline constant** `MAX` = `100` (all occurrences)  
target: field 'MAX'

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