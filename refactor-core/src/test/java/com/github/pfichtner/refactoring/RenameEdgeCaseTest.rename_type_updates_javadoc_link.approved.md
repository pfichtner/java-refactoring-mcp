# Rename type: Greeter → HelloService ({@link} and @see updated)


### Input: App.java:
```java
package com.example;

/**
 * Application entry point.
 * Uses {@link Greeter} to generate greetings.
 *
 * @see Greeter
 */
public class App {
    private Greeter greeter = new Greeter();

    public void run() {
        System.out.println(greeter.greet("World"));
    }
}
```

### Input: Greeter.java:
```java
package com.example;

public class Greeter {
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

### Refactoring:
**rename type** `Greeter` → `HelloService`  
{@link Greeter} and @see Greeter in App.java Javadoc must be updated

### Output: App.java:
```java
package com.example;

/**
 * Application entry point.
 * Uses {@link HelloService} to generate greetings.
 *
 * @see HelloService
 */
public class App {
    private HelloService greeter = new HelloService();

    public void run() {
        System.out.println(greeter.greet("World"));
    }
}
```

### Output: HelloService.java:
```java
package com.example;

public class HelloService {
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

### Filesystem:
- `Greeter.java` → `HelloService.java`