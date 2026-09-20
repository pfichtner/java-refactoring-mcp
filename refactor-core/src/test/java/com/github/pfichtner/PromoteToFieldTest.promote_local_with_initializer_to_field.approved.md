# Promote local variable result → private field


### Input:
```java
public class Calc {

    int compute(int x) {
        int result = x * 2;
        return result;
    }
}
```

### Refactoring:
**promote to field** `int result = x * 2;` → `private int result;` field + assignment  
line 4, col 9

### Output:
```java
public class Calc {
    private int result;

    int compute(int x) {
        result = x * 2;
        return result;
    }
}
```