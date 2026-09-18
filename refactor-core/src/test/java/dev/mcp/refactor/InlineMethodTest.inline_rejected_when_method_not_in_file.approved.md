# Inline method — rejected: method not in this file


### Input:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
    }
}
```

### Refactoring:
**inline method** `println(...)` at line 3, col 20  
method declared in java.io.PrintStream — only single-file inline supported

### Diagnostic:
```
Method 'println' is not declared in this file. Use the project-based overload for cross-file inline.
```