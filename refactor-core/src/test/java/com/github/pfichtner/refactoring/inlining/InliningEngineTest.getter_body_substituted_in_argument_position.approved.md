# Aggressive inline: getter substituted in argument position


### Input:
```java
public class Foo {
    private int value = 21;
    private int counter = 0;

    public int run() {
        return tick() + getValue();
    }

    private int tick() {
        return counter++;
    }

    private int getValue() {
        return this.value;
    }
}
```

### Refactoring:
**inline method** `getValue()` at line 6, col 25  
single-return field getter; substituted in-place after impure left operand

### Output:
```java
public class Foo {
    private int value = 21;
    private int counter = 0;

    public int run() {
        return tick() + this.value;
    }

    private int tick() {
        return counter++;
    }

    private int getValue() {
        return this.value;
    }
}
```