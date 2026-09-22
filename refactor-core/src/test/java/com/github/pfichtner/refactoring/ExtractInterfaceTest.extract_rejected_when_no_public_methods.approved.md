# Extract interface — rejected: no public non-static methods


### Input: Foo.java:
```java
public class Foo {
    private void helper() {
        System.out.println("private");
    }

    static void staticHelper() {
        System.out.println("static");
    }
}
```

### Refactoring:
**extract interface** `Foo` → `FooInterface`  
all methods are private or static — nothing to extract

### Diagnostic:
```
No public non-static methods found — nothing to extract.
```