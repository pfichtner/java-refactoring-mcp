# Inline method: greet() — all occurrences, declaration kept


### Input:
```java
public class Foo {
    public void run() {
        greet();
        greet();
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```

### Refactoring:
**inline method** `greet()` — all occurrences in file, declaration kept  
both call sites expanded in-place; greet() declaration stays

### Output:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.out.println("World");
        System.out.println("Hello");
        System.out.println("World");
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```