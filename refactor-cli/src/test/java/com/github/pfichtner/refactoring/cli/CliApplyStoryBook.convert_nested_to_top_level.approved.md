# convert-nested


### Command:
```
convert-nested --file {root}/Outer.java --line 15 --column 25
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### Helper.java:
```java
package com.example;

public class Helper {

    static int doubleIt(int x) {
        return x * 2;
    }
}
```

### Outer.java:
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