# Extract constant: "HELLO" → GREETING (replace all)


### Input:
```java
public class Foo {
    public void run() {
        System.out.println("HELLO");
        System.err.println("HELLO");
        String msg = "HELLO" + "!";
    }
}
```

### Refactoring:
**extract constant** `"HELLO"` → `private static final String GREETING` (replaceAll=true)  
line 3, col 28

### Output:
```java
public class Foo {
    private static final String GREETING = "HELLO";
    public void run() {
        System.out.println(GREETING);
        System.err.println(GREETING);
        String msg = GREETING + "!";
    }
}
```