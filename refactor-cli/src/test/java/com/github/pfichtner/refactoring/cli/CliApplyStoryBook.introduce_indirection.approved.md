# introduce-indirection


### Command:
```
introduce-indirection --file {root}/MathUtils.java --line 3 --column 26 --name computeSquare
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### MathUtils.java:
```java
public class MathUtils {

    public static int square(int x) {
        return x * x;
    }


    public static int computeSquare(int x) {
        return square(x);
    }
}
```