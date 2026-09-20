# Inline method: greet() — all occurrences, declaration removed


### Input:
```java
public class Foo {
    public void run() {
        greet();
        greet();
    }

    private void greet() {
        System.out.println("Hello");
    }
}
```

### Refactoring:
**inline method** `greet()` — all occurrences, declaration removed  
both call sites expanded; greet() declaration deleted

### Output:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.out.println("Hello");
    }

}
```