# Inline variable: msg — replaces all two uses


### Input:
```java
public class Foo {
    public void run() {
        String msg = "Hello";
        System.out.println(msg);
        System.err.println(msg);
    }
}
```

### Refactoring:
**inline variable** `msg` = `"Hello"` (2 uses)  
declaration at line 3, col 16

### Output:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.err.println("Hello");
    }
}
```