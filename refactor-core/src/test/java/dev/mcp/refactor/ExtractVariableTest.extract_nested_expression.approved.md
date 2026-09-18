# Extract variable: name.toUpperCase() → upper


### Input:
```java
public class Foo {
    public String greet(String name) {
        return "Hello, " + name.toUpperCase();
    }
}
```

### Refactoring:
**extract variable** `name.toUpperCase()` → `String upper`  
line 3, col 28

### Output:
```java
public class Foo {
    public String greet(String name) {
        String upper = name.toUpperCase();
        return "Hello, " + upper;
    }
}
```