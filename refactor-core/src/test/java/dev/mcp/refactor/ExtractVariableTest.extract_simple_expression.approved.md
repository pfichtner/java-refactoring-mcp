# Extract variable: 6 * 7 → answer


### Input:
```java
public class Foo {
    public int compute() {
        return 6 * 7;
    }
}
```

### Refactoring:
**extract variable** `6 * 7` → `int answer`  
line 3, col 16

### Output:
```java
public class Foo {
    public int compute() {
        int answer = 6 * 7;
        return answer;
    }
}
```