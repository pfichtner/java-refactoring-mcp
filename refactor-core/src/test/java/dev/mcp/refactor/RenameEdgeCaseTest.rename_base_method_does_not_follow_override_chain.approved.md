# Rename method: Animal.speak → makeSound (override chain limitation)


### Input: Animal.java:
```java
package com.example;

public class Animal {
    public void speak() {
        System.out.println("...");
    }
}
```

### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Animal a = new Animal();
        a.speak();
        Dog d = new Dog();
        d.speak();
    }
}
```

### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    @Override
    public void speak() {
        System.out.println("woof");
    }
}
```

### Refactoring:
**rename method** `Animal.speak` → `makeSound`  
KNOWN LIMITATION: Dog.speak() override is NOT renamed — the engine matches by binding key and does not follow virtual dispatch chains

### Output: Animal.java:
```java
package com.example;

public class Animal {
    public void makeSound() {
        System.out.println("...");
    }
}
```

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Animal a = new Animal();
        a.makeSound();
        Dog d = new Dog();
        d.speak();
    }
}
```