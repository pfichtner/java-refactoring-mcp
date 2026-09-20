# Remove param by name: add.c


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        int result = calc.add(1, 2, 99);
        System.out.println(result);
    }
}
```

### Input: Computation.java:
```java
package com.example;

public class Computation {
    public int add(int a, int b, int c) {
        return a + b;
    }
}
```

### Refactoring:
**remove param** `c` from `add`  
target: parameter 'c' in method 'add'

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        int result = calc.add(1, 2);
        System.out.println(result);
    }
}
```

### Output: Computation.java:
```java
package com.example;

public class Computation {
    public int add(int a, int b) {
        return a + b;
    }
}
```