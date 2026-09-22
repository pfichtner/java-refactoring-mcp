# Pull up field: Dog.breed → Animal


### Input: Animal.java:
```java
package com.example;

public class Animal {
    protected String name;

    public String name() {
        return name;
    }
}
```

### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    protected String name;
    protected String breed;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
```

### Refactoring:
**pull up field** `Dog.breed` → `Animal`  
line 5, col 22

### Output: Animal.java:
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

### Output: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    protected String name;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
```