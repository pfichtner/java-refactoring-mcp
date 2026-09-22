# Rename type name — expect rejection


### Input:
```java
public class Foo {
    public int compute() {
        int x = 6;
        int y = x * 7;
        return y;
    }
}
```

### Refactoring:
**rename** `Foo` → `Bar`  
target: type name at line 1, col 14 (not a local variable or parameter)

### Diagnostic:
```
Element at offset 13 is not a local variable or parameter.
```