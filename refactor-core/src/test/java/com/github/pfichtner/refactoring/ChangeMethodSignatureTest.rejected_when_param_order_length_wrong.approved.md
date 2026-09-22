# Change method signature rejected: wrong paramOrder length


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
**change method signature** `convert` — paramOrder length 1 but method has 2 params  


### Diagnostic:
```
paramOrder length 1 does not match parameter count 2.
```