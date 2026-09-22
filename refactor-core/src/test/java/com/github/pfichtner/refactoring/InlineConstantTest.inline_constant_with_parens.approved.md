# Inline constant: FACTOR — compound initializer needs parens


### Input:
```java
public class Foo {
    private static final int FACTOR = 3 + 4;

    public int compute(int x) {
        return x * FACTOR;
    }
}
```

### Refactoring:
**inline constant** `FACTOR` — all occurrences, declaration kept  
InfixExpression initializer wrapped in parens to preserve precedence

### Output:
```java
public class Foo {
    private static final int FACTOR = 3 + 4;

    public int compute(int x) {
        return x * (3 + 4);
    }
}
```