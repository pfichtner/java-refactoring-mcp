# Promote local variable count (no initializer) → private field


### Input:
```java
public class Counter {

    void reset() {
        int count;
        count = 0;
        System.out.println(count);
    }
}
```

### Refactoring:
**promote to field** `int count;` (no initializer) → `private int count;` field, declaration removed  
line 4, col 9

### Output:
```java
public class Counter {
    private int count;

    void reset() {
        count = 0;
        System.out.println(count);
    }
}
```