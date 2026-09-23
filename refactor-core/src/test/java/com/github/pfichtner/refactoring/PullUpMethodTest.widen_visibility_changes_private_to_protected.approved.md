# Pull up method with widen_visibility: private Dog.bark → protected Animal.bark


### Input: Animal.java:
```java
package com.example;

public class Animal {
}
```

### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    private void bark() {
        System.out.println("Woof!");
    }
}
```

### Refactoring:
**pull up method** `Dog.bark()` → `Animal` (widen_visibility=true)  
line 4, col 18

### Output: Animal.java:
```java
package com.example;

public class Animal {

    protected void bark() {
        System.out.println("Woof!");
    }
}
```

### Output: Dog.java:
```java
package com.example;

public class Dog extends Animal {
}
```