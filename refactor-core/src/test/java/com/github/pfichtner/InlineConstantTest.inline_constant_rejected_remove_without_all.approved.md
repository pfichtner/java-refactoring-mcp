# Inline constant — rejected: removeDeclaration without allOccurrences


### Input:
```java
public class Foo {
    private static final int MAX = 100;

    public boolean isValid(int x) {
        return x <= MAX;
    }

    public int clamp(int x) {
        return Math.min(x, MAX);
    }
}
```

### Refactoring:
**inline constant** `MAX` — allOccurrences=false, removeDeclaration=true  
declaration removal requires all occurrences to be inlined

### Diagnostic:
```
removeDeclaration requires allOccurrences=true — cannot remove declaration when only one occurrence is inlined.
```