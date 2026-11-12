# Aggressive inline: private final int field


### Input:
```java
public class Foo {
    private final int VALUE = 42;

    public int run() {
        return VALUE;
    }
}
```

### Refactoring:
**inline all** `VALUE`  
private final int field replaced by its literal initializer

### Output:
```java
public class Foo {
    private final int VALUE = 42;

    public int run() {
        return 42;
    }
}
```