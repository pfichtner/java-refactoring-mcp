# Convert anonymous rejected: no anonymous class at offset


### Input:
```java
public class Outer {

    void start() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println("running");
            }
        };
        r.run();
    }
}
```

### Refactoring:
**convert anonymous to nested** offset points to `void start()`, not an anonymous class  
line 3, col 5

### Diagnostic:
```
No anonymous class declaration found at offset 26.
```