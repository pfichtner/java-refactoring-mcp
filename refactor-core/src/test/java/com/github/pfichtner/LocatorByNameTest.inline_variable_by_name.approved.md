# Inline variable by name: x


### Input:
```java
public class Foo {
    public int compute() {
        int x = 6 * 7;
        return x;
    }
}
```

### Refactoring:
**inline variable** `x` = `6 * 7`  
target: variable 'x'

### Output:
```java
public class Foo {
    public int compute() {
        return (6 * 7);
    }
}
```