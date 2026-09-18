# Rename local variable: y → product (x must be untouched)


### Input:
```java
public class Foo {
    public int compute() {
        int x = 6;
        int y = x * 7;
        return y;
    }
}
```

### Refactoring:
**rename local variable** `y` → `product`  
target: declaration site at line 4, col 13

### Output:
```java
public class Foo {
    public int compute() {
        int x = 6;
        int product = x * 7;
        return product;
    }
}
```