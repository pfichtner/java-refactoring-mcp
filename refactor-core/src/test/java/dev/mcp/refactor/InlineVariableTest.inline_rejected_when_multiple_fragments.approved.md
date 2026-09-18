# Inline variable — rejected: multiple fragments in declaration


### Input:
```java
public class Foo {
    public void run() {
        int x = 1, y = 2;
        System.out.println(x + y);
    }
}
```

### Refactoring:
**inline variable** `x` from `int x = 1, y = 2`  
declaration at line 3, col 13

### Diagnostic:
```
Cannot inline 'x': declaration contains multiple variables. Split it first.
```