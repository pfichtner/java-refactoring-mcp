# Aggressive inline: static final int constant


### Input:
```java
public class Foo {
    private static final int LIMIT = 100;

    public int run() {
        return LIMIT;
    }
}
```

### Refactoring:
**inline all** `LIMIT`  
static final int constant replaced by its literal value

### Output:
```java
public class Foo {
    private static final int LIMIT = 100;

    public int run() {
        return 100;
    }
}
```