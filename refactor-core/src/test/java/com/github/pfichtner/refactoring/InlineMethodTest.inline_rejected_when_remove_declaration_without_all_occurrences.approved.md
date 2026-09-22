# Inline method — rejected: removeDeclaration without allOccurrences


### Input:
```java
public class Foo {
    public void run() {
        greet();
        System.out.println("done");
    }

    private void greet() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```

### Refactoring:
**inline method** `greet()` — allOccurrences=false, removeDeclaration=true  
declaration removal requires all occurrences to be inlined

### Diagnostic:
```
removeDeclaration requires allOccurrences=true — cannot remove declaration when only one call site is inlined.
```