# Convert nested class Helper → top-level class Helper.java


### Input: Outer.java:
```java
package com.example;

public class Outer {

    private String name;

    public Outer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static class Helper {

        static int doubleIt(int x) {
            return x * 2;
        }
    }
}
```

### Refactoring:
**convert nested to top-level** `private static class Helper` → new file `Helper.java`  
line 15, col 13

### Output: Outer.java:
```java
package com.example;

public class Outer {

    private String name;

    public Outer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

### New file: Helper.java:
```java
package com.example;

public class Helper {

    static int doubleIt(int x) {
        return x * 2;
    }
}
```