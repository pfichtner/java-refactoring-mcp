# inline-const


### Command:
```
inline-const --file {root}/Foo.java --line 5 --column 21 --all-occurrences --remove-declaration
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
public class Foo {

    public boolean isValid(int x) {
        return x <= 100;
    }

    public int clamp(int x) {
        return Math.min(x, 100);
    }
}
```