# Aggressive inline: static final String constant


### Input:
```java
public class Foo {
    private static final String PREFIX = "hello";

    public String run() {
        return PREFIX + " world";
    }
}
```

### Refactoring:
**inline all** `PREFIX`  
static final String constant replaced by its literal value

### Output:
```java
public class Foo {
    private static final String PREFIX = "hello";

    public String run() {
        return "hello" + " world";
    }
}
```