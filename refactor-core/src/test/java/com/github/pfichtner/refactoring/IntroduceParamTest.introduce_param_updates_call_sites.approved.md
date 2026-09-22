# Introduce parameter: "World" → String name (multi-file)


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        g.greet();
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
in Greeter.greet(); call sites updated with original argument

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        g.greet("World");
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