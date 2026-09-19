# Inline variable: x (initializer is a compound expression)


### Input:
```java
public class Foo {
    public int compute() {
        int x = 6 * 7;
        return x;
    }
}
```

### Refactoring:
**inline variable** `x` = `6 * 7`  
declaration at line 3, col 13

### Output:
```java
public class Foo {
    public int compute() {
        return (6 * 7);
    }
}
```