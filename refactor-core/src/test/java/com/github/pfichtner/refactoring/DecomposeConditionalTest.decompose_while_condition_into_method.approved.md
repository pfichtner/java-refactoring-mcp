# Decompose conditional: while (count < max && max > 0) → shouldContinue()


### Input:
```java
public class Foo {
    private int count;
    private int max;

    public void process() {
        while (count < max && max > 0) {
            count++;
        }
    }
}
```

### Refactoring:
**decompose conditional** `while (count < max && max > 0)` → `while (shouldContinue())`  
condition extracted into private boolean method

### Output:
```java
public class Foo {
    private int count;
    private int max;

    public void process() {
        while (shouldContinue()) {
            count++;
        }
    }


    private boolean shouldContinue() {
        return count < max && max > 0;
    }
}
```