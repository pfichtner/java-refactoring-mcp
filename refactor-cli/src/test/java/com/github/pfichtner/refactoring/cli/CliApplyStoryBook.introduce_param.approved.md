# introduce-param


### Command:
```
introduce-param --file {root}/src/main/java/com/example/Greeter.java --start-line 5 --start-column 40 --end-line 5 --end-column 47 --name whom
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        g.greet("World");
    }
}
```

### src/main/java/com/example/Greeter.java:
```java
package com.example;

public class Greeter {
    public void greet(java.lang.String whom) {
        System.out.println("Hello, " + whom);
    }
}
```