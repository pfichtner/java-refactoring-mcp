# Inline method: add() — all occurrences, declaration kept


### Input:
```java
public class Foo {
    public void run() {
        int x = 3;
        int y = 4;
        int a = add(x, y);
        int b = add(1, 2);
    }

    private int add(int p, int q) {
        return p + q;
    }
}
```

### Refactoring:
**inline method** `add(...)` — all occurrences in file, declaration kept  
both call sites replaced with return expression; add() declaration stays

### Output:
```java
public class Foo {
    public void run() {
        int x = 3;
        int y = 4;
        int a = x + y;
        int b = 1 + 2;
    }

    private int add(int p, int q) {
        return p + q;
    }
}
```