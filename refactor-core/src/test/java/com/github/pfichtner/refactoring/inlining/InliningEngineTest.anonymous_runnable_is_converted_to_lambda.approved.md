# Aggressive inline: anonymous Runnable flattened to lambda


### Input:
```java
class Foo {
    Runnable make() {
        return new Runnable() {
            @Override
            public void run() {
                System.out.println("hello");
            }
        };
    }
}
```

### Refactoring:
**flatten anonymous class** anonymous Runnable  
single-statement void body; expression-form lambda

### Output:
```java
class Foo {
    Runnable make() {
        return () -> System.out.println("hello");
    }
}
```