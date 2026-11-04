# Aggressive inline — rejected: non-completing void body


### Input:
```java
public class Foo {
    public int run() {
        loop();
        return 1;
    }

    private void loop() {
        while (true) {
        }
    }
}
```

### Refactoring:
**inline method** `loop()` at line 3, col 9  
pasting an infinite loop before the return makes following code unreachable

### Diagnostic:
```
method body cannot complete normally; inlining could make following code unreachable
```