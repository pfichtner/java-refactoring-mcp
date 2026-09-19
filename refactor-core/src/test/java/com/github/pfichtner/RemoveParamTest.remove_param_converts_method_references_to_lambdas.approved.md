# Remove parameter: method references converted to lambdas


### Input: App.java:
```java
package com.example;

import java.util.function.IntBinaryOperator;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        IntBinaryOperator op = calc::add;
        int result = calc.add(1, 2);
        System.out.println(result);
    }
}
```

### Input: Computation.java:
```java
package com.example;

public class Computation {
    public int add(int a, int b) {
        return a;
    }
}
```

### Refactoring:
**remove parameter** `int b` at index 1 of `add(int a, int b)`  
line 4, col 31 in Computation.java — b not used in body

### Output: App.java:
```java
package com.example;

import java.util.function.IntBinaryOperator;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        IntBinaryOperator op = (a, b) -> calc.add(a);
        int result = calc.add(1);
        System.out.println(result);
    }
}
```

### Output: Computation.java:
```java
package com.example;

public class Computation {
    public int add(int a) {
        return a;
    }
}
```