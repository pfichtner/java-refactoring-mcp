# Aggressive inline: getter substituted in assignment RHS despite side-effecting LHS


### Input:
```java
public class Foo {
    private int[] values = new int[3];

    public void run() {
        values[index()] = compute();
    }

    private int index() {
        return 0;
    }

    private int compute() {
        return 1;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 5, col 27  
single-return pure getter substituted in-place; LHS evaluation order preserved

### Output:
```java
public class Foo {
    private int[] values = new int[3];

    public void run() {
        values[index()] = 1;
    }

    private int index() {
        return 0;
    }

    private int compute() {
        return 1;
    }
}
```