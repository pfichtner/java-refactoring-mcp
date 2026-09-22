# pull-up-field


### Command:
```
pull-up-field --file {root}/src/main/java/com/example/Dog.java --field breed
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/Animal.java:
```java
package com.example;

public class Animal {
    protected String name;
    protected String breed;

    public String name() {
        return name;
    }
}
```

### src/main/java/com/example/Dog.java:
```java
package com.example;

public class Dog extends Animal {
    protected String name;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
```