# Change method signature: reorder params convert(int,String) → convert(String,int)


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
**change method signature** `convert(int value, String prefix)` → `convert(String prefix, int value)`  
paramOrder: [1, 0] — all call sites updated

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Converter c = new Converter();
        System.out.println(c.convert("val:", 42));
        System.out.println(c.convert("id:", 7));
    }
}
```

### Output: Converter.java:
```java
package com.example;

public class Converter {
    public String convert(String prefix, int value) {
        return prefix + value;
    }
}
```