# Aggressive inline: private final field via this reference


### Input:
```java
public class Foo {
    private final int VALUE = 42;

    public int run() {
        return this.VALUE;
    }
}
```

### Refactoring:
**inline all** `this.VALUE`  
explicit this.field access replaced by the literal initializer

### Output:
```java
public class Foo {
    private final int VALUE = 42;

    public int run() {
        return 42;
    }
}
```