# Aggressive inline: private final field with arithmetic initializer


### Input:
```java
public class Foo {
    private final int BASE = 2 * 3 + 1;

    public int run() {
        return BASE * 10;
    }
}
```

### Refactoring:
**inline all** `BASE`  
compound initializer wrapped in parens to preserve operator precedence

### Output:
```java
public class Foo {
    private final int BASE = 2 * 3 + 1;

    public int run() {
        return (2 * 3 + 1) * 10;
    }
}
```