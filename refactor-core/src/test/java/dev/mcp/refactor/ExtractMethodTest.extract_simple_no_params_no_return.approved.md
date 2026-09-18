# Extract method: greet() — no params, no return


### Input:
```java
public class Greeter {
    public void run() {
        System.out.println("Hello");
        System.out.println("World");
        int x = 42;
    }
}
```

### Refactoring:
**extract method** selection → `greet()`  
line 3, col 9 to line 4, col 36

### Output:
```java
public class Greeter {
    public void run() {
        greet();
        int x = 42;
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```