# Extract method: add(a, b) — with return value


### Input:
```java
public class Computation {
    public int run() {
        int a = 3;
        int b = 4;
        int sum = a + b;
        return sum * 2;
    }
}
```

### Refactoring:
**extract method** selection → `int add(int a, int b)`  
line 5, col 9

### Output:
```java
public class Computation {
    public int run() {
        int a = 3;
        int b = 4;
        int sum = add(a, b);
        return sum * 2;
    }

    private int add(int a, int b) {
        int sum = a + b;
        return sum;
    }
}
```