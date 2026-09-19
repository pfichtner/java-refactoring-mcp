# Extract variable: 42 → MAGIC (replace all occurrences)


### Input:
```java
public class Foo {
    public void run() {
        System.out.println(42);
        System.err.println(42);
        int x = 42 + 1;
    }
}
```

### Refactoring:
**extract variable** `42` → `int MAGIC` (replaceAll=true)  
line 3, col 28

### Output:
```java
public class Foo {
    public void run() {
        int MAGIC = 42;
        System.out.println(MAGIC);
        System.err.println(MAGIC);
        int x = MAGIC + 1;
    }
}
```