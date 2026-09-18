# Pull up method: Dog.speak → Animal


### Input: Animal.java:
```java
package com.example;

public class Animal {
    public String name() {
        return "Animal";
    }
}
```

### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}
```

### Refactoring:
**pull up method** `Dog.speak()` → `Animal`  
line 9, col 17

### Output: Animal.java:
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

### Output: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }
}
```