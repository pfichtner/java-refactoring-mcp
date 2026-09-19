# Pull up method: Dog.speak → Animal (FQN extends clause)


### Input: Animal.java:
```java
package com.example.base;

public class Animal {
    public String name() {
        return "Animal";
    }
}
```

### Input: Dog.java:
```java
package com.example.derived;

// Fully-qualified extends clause — no import for Animal
public class Dog extends com.example.base.Animal {
    public void speak() {
        System.out.println("Woof!");
    }
}
```

### Refactoring:
**pull up method** `Dog.speak()` → `Animal`  
line 5, col 17

### Output: Animal.java:
```java
package com.example.base;

public class Animal {
    public String name() {
        return "Animal";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}
```

### Output: Dog.java:
```java
package com.example.derived;

// Fully-qualified extends clause — no import for Animal
public class Dog extends com.example.base.Animal {
}
```