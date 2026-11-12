# Aggressive inline: private final String field


### Input:
```java
public class Foo {
    private final String PREFIX = "hello";

    public String run() {
        return PREFIX + " world";
    }
}
```

### Refactoring:
**inline all** `PREFIX`  
private final String field replaced by its literal initializer

### Output:
```java
public class Foo {
    private final String PREFIX = "hello";

    public String run() {
        return "hello" + " world";
    }
}
```