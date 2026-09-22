# inline-method


### Command:
```
inline-method --file {root}/src/main/java/com/example/App.java --line 6 --column 27
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### src/main/java/com/example/App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = 1 + 2;
        System.out.println(result);
    }
}
```