# remove-param


### Command:
```
remove-param --file {root}/src/main/java/com/example/Computation.java --method add --parameter c
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/App.java:
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

### src/main/java/com/example/Computation.java:
```java
package com.example;

public class Computation {
    public int add(int a, int b) {
        return a + b;
    }
}
```