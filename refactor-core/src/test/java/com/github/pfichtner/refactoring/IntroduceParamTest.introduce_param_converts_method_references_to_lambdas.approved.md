# Introduce parameter: method references converted to lambdas


### Input: App.java:
```java
package com.example;

import java.util.function.Consumer;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        Runnable r = g::greet;
        Consumer<Greeter> c = Greeter::greet;
    }
}
```

### Input: Greeter.java:
```java
package com.example;

public class Greeter {
    public void greet() {
        System.out.println("Hello, " + "World");
    }
}
```

### Refactoring:
**introduce parameter** `"World"` → parameter `String name`  
in Greeter.greet(); method references updated to lambda form

### Output: App.java:
```java
package com.example;

import java.util.function.Consumer;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        Runnable r = () -> g.greet("World");
        Consumer<Greeter> c = greeter -> greeter.greet("World");
    }
}
```

### Output: Greeter.java:
```java
package com.example;

public class Greeter {
    public void greet(String name) {
        System.out.println("Hello, " + name);
    }
}
```