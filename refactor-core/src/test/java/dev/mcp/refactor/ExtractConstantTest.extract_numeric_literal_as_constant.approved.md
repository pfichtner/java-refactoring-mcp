# Extract constant: 3.14159 → PI


### Input:
```java
public class Foo {
    public double area(double r) {
        return 3.14159 * r * r;
    }
}
```

### Refactoring:
**extract constant** `3.14159` → `private static final double PI`  
line 3, col 16

### Output:
```java
public class Foo {
    private static final double PI = 3.14159;
    public double area(double r) {
        return PI * r * r;
    }
}
```