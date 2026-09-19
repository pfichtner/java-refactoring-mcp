# Inline method: add(x, y) → return expression substituted


### Input:
```java
public class Foo {
    public int run() {
        int x = 3;
        int y = 4;
        int result = add(x, y);
        return result;
    }

    private int add(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**inline method** `add(x, y)` at line 5, col 22  
return expression `a + b` with a→x, b→y replaces call

### Output:
```java
public class Foo {
    public int run() {
        int x = 3;
        int y = 4;
        int result = x + y;
        return result;
    }

    private int add(int a, int b) {
        return a + b;
    }
}
```