# Rename local variable: x → answer


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
**rename local variable** `x` → `answer`  
target: declaration site at line 3, col 13

### Output:
```java
public class Foo {
    public int compute() {
        int answer = 6;
        int y = answer * 7;
        return y;
    }
}
```