# Decompose conditional rejected: method already exists


### Input:
```java
public class Foo {
    int age;
    void m() { if (age > 18 && age < 65) {} }
    private boolean isEligible() { return true; }
}
```

### Refactoring:
**decompose conditional** `if (age > 18 && age < 65)` → `isEligible()` — method name already taken  


### Diagnostic:
```
Method 'isEligible()' already exists in Foo.
```