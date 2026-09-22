# Introduce indirection: MathUtils.square → static computeSquare(int)


### Input:
```java
public class MathUtils {

    public static int square(int x) {
        return x * x;
    }
}
```

### Refactoring:
**introduce indirection** `static square(int)` → adds `public static int computeSquare(int x)` that delegates  
line 3, col 23

### Output:
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