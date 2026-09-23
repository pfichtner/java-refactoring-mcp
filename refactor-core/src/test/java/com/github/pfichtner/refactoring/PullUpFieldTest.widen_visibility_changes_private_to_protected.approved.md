# Pull up field with widen_visibility: private Dog.secret → protected Animal.secret


### Input: Animal.java:
```java
package com.example;

public class Animal {
    protected String name;
}
```

### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    private String secret;
}
```

### Refactoring:
**pull up field** `Dog.secret` → `Animal` (widen_visibility=true)  
line 4, col 20

### Output: Animal.java:
```java
package com.example;

public class Animal {
    protected String name;
    protected String secret;
}
```

### Output: Dog.java:
```java
package com.example;

public class Dog extends Animal {
}
```