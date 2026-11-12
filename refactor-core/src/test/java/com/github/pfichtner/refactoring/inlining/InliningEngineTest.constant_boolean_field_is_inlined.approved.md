# Aggressive inline: static final boolean constant


### Input:
```java
public class Foo {
    static final boolean ENABLED = true;

    public boolean run() {
        return ENABLED;
    }
}
```

### Refactoring:
**inline all** `ENABLED`  
static final boolean constant replaced by its literal value

### Output:
```java
public class Foo {
    static final boolean ENABLED = true;

    public boolean run() {
        return true;
    }
}
```