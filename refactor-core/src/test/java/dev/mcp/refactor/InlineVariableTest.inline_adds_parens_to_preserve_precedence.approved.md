# Inline variable: sum — parentheses added to preserve precedence


### Input:
```java
public class Foo {
    public int compute(int a, int b) {
        int sum = a + b;
        return sum * 2;
    }
}
```

### Refactoring:
**inline variable** `sum` = `a + b` (used in `sum * 2`)  
declaration at line 3, col 13

### Output:
```java
public class Foo {
    public int compute(int a, int b) {
        return (a + b) * 2;
    }
}
```