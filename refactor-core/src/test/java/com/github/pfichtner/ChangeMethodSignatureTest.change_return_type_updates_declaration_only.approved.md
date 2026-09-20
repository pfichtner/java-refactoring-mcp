# Change method signature: change return type String → Object


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
**change method signature** `String convert(...)` → `Object convert(...)`  
declaration only — call sites unchanged

### Output: Converter.java:
```java
package com.example;

public class Converter {
    public Object convert(int value, String prefix) {
        return prefix + value;
    }
}
```