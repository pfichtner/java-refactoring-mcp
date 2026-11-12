# Aggressive inline: delegation substituted at nested position (inlineAt)


### Input:
```java
class Foo {
    private int twice(int n) {
        return n * 2;
    }

    private int doubled(int n) {
        return twice(n);
    }

    int nonInlineable(int x) {
        return x + 10;
    }

    int compute(int n) {
        return nonInlineable(n) + doubled(n);
    }
}
```

### Refactoring:
**inline method** `doubled(n)` at line 15, col 35  
delegation body `return twice(n)` substituted in-place; hoisting is impossible because the left operand `nonInlineable(n)` is a method call

### Output:
```java
class Foo {
    private int twice(int n) {
        return n * 2;
    }

    private int doubled(int n) {
        return twice(n);
    }

    int nonInlineable(int x) {
        return x + 10;
    }

    int compute(int n) {
        return nonInlineable(n) + twice(n);
    }
}
```