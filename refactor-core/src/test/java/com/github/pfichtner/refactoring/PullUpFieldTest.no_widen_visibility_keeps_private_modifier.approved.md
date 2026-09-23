# Pull up field without widen_visibility: private modifier preserved


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
**pull up field** `Dog.secret` → `Animal` (widen_visibility=false)  
line 4, col 20

### Output: Animal.java:
```java
package com.example;

public class Animal {
    protected String name;
    private String secret;
}
```

### Output: Dog.java:
```java
package com.example;

public class Dog extends Animal {
}
```