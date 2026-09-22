# Rename local variable: x → local (field x must be untouched)


### Input:
```java
public class Shadow {
    private int x = 10;

    public int compute() {
        int x = 20; // shadows the field
        return x + 1;
    }
}
```

### Refactoring:
**rename local variable** `x` → `local`  
target: line 5, col 13 (local shadows field `this.x`)

### Output:
```java
public class Shadow {
    private int x = 10;

    public int compute() {
        int local = 20; // shadows the field
        return local + 1;
    }
}
```