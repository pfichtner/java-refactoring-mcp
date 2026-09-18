# Inline method: printSum(a, b) — void, parameters substituted


### Input:
```java
public class Foo {
    public void run() {
        int a = 3;
        int b = 4;
        printSum(a, b);
    }

    private void printSum(int x, int y) {
        System.out.println(x + y);
    }
}
```

### Refactoring:
**inline method** `printSum(a, b)` at line 5, col 9  
parameters x→a, y→b substituted in body

### Output:
```java
public class Foo {
    public void run() {
        int a = 3;
        int b = 4;
        System.out.println(a + b);
    }

    private void printSum(int x, int y) {
        System.out.println(x + y);
    }
}
```