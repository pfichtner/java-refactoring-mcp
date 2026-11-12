# Aggressive inline: anonymous Comparator flattened to lambda


### Input:
```java
import java.util.Comparator;

class Foo {
    Comparator<String> byLength() {
        return new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(a.length(), b.length());
            }
        };
    }
}
```

### Refactoring:
**flatten anonymous class** anonymous Comparator with two parameters  
single-return body; expression-form lambda with inferred parameter types

### Output:
```java
import java.util.Comparator;

class Foo {
    Comparator<String> byLength() {
        return (a, b) -> Integer.compare(a.length(), b.length());
    }
}
```