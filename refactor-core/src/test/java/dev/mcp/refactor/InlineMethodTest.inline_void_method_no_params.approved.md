# Inline method: greet() — void, no parameters


### Input:
```java
public class Foo {
    public void run() {
        greet();
        System.out.println("done");
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```

### Refactoring:
**inline method** `greet()` at line 3, col 9  
void method — body statements replace the call

### Output:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.out.println("World");
        System.out.println("done");
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```