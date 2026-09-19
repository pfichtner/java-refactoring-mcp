# Push down method: Animal.speak → Dog (FQN extends clause)


### Input: Animal.java:
```java
package com.example.base;

public class Animal {
    public String name() {
        return "Animal";
    }

    public void speak() {
        System.out.println("...");
    }
}
```

### Input: Dog.java:
```java
package com.example.derived;

// Fully-qualified extends clause — no import for Animal
public class Dog extends com.example.base.Animal {
}
```

### Refactoring:
**push down method** `Animal.speak()` → subclasses  
line 8, col 17

### Output: Animal.java:
```java
package com.example.base;

public class Animal {
    public String name() {
        return "Animal";
    }
}
```

### Output: Dog.java:
```java
package com.example.derived;

// Fully-qualified extends clause — no import for Animal
public class Dog extends com.example.base.Animal {

    public void speak() {
        System.out.println("...");
    }
}
```