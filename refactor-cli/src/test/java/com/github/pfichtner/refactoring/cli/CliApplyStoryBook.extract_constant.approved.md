# extract-const


### Command:
```
extract-const --file {root}/Foo.java --start-line 3 --start-column 16 --end-line 3 --end-column 23 --name PI
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
public class Foo {
    private static final double PI = 3.14159;
    public double area(double r) {
        return PI * r * r;
    }
}
```