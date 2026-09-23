# Change method signature: change param type int → long (declaration only)


### Input: Converter.java:
```java
package com.example;

public class Converter {
    public String convert(int value, String prefix) {
        return prefix + value;
    }
}
```

### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Converter c = new Converter();
        System.out.println(c.convert(42, "val:"));
        System.out.println(c.convert(7, "id:"));
    }
}
```

### Refactoring:
**change method signature** `convert(int value, String prefix)` → `convert(long value, String prefix)`  
declaration only — call sites unchanged

### Output: Converter.java:
```java
package com.example;

public class Converter {
    public String convert(long value, String prefix) {
        return prefix + value;
    }
}
```