# Change method signature rejected: nothing to change


### Input: Converter.java:
```java
package com.example;

public class Converter {
    public String convert(int value, String prefix) {
        return prefix + value;
    }
}
```

### Refactoring:
**change method signature** `convert` — no newReturnType, no paramOrder  


### Diagnostic:
```
Nothing to change: provide newReturnType, paramOrder, or both.
```