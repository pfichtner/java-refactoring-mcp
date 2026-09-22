# Inline constant — rejected: mutable field


### Input:
```java
public class Foo {
    private int count = 0;

    public void run() {
        System.out.println(count);
    }
}
```

### Refactoring:
**inline constant** `count` — not a static final field  
only static final constants can be inlined

### Diagnostic:
```
Cannot inline field 'count': only static final constants are supported.
```