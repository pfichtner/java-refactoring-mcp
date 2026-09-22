# Rename method: compute(int,int) → add (sibling overload unchanged)


### Input: App.java:
```java
package com.example;

public class App {
    public void run() {
        Overloaded o = new Overloaded();
        int r2 = o.compute(1, 2);
        int r3 = o.compute(1, 2, 3);
    }
}
```

### Input: Overloaded.java:
```java
package com.example;

public class Overloaded {
    public int compute(int a, int b) {
        return a + b;
    }

    public int compute(int a, int b, int c) {
        return a + b + c;
    }
}
```

### Refactoring:
**rename method** `Overloaded.compute(int,int)` → `add`  
only the 2-arg overload and its call sites are renamed; 3-arg compute unchanged

### Output: App.java:
```java
package com.example;

public class App {
    public void run() {
        Overloaded o = new Overloaded();
        int r2 = o.add(1, 2);
        int r3 = o.compute(1, 2, 3);
    }
}
```

### Output: Overloaded.java:
```java
package com.example;

public class Overloaded {
    public int add(int a, int b) {
        return a + b;
    }

    public int compute(int a, int b, int c) {
        return a + b + c;
    }
}
```