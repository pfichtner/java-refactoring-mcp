# Inline constant — rejected: multiple constants in one declaration


### Input:
```java
public class Foo {
    private static final int X = 1, Y = 2;

    public int run() {
        return X;
    }
}
```

### Refactoring:
**inline constant** `X` — declaration has multiple fragments  
split the declaration before inlining

### Diagnostic:
```
Cannot inline 'X': declaration contains multiple constants. Split it first.
```