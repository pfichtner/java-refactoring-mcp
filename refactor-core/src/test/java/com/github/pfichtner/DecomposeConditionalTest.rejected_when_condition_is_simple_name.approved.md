# Decompose conditional rejected: condition is too simple (single name)


### Input:
```java
public class Foo { boolean flag; void m() { if (flag) {} } }
```

### Refactoring:
**decompose conditional** `if (flag)` — single name, nothing to decompose  


### Diagnostic:
```
Condition is too simple to decompose: `flag`. Only compound conditions are eligible.
```