# Rename parameter: n → count


### Input:
```java
public class Counter {
    public int sum(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }
}
```

### Refactoring:
**rename parameter** `n` → `count`  
target: declaration site at line 2, col 24

### Output:
```java
public class Counter {
    public int sum(int count) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += i;
        }
        return total;
    }
}
```