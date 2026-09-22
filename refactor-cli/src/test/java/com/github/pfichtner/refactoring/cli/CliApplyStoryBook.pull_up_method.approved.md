# pull-up


### Command:
```
pull-up --file {root}/src/main/java/com/example/Dog.java --method speak
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/Animal.java:
```java
package com.example;

public class Animal {
    public String name() {
        return "Animal";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}
```

### src/main/java/com/example/Dog.java:
```java
package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }
}
```