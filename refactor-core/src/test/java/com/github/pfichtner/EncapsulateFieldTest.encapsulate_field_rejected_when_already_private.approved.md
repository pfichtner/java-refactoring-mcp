# Encapsulate field: Person.age — getter only (verify private result)


### Input: Person.java:
```java
package com.example;

public class Person {
    public String name;
    public int age;
}
```

### Refactoring:
**encapsulate field** `public int age` → `private int age` + `getAge()`  
getter generated, no external read sites

### Output: Person.java:
```java
package com.example;

public class Person {
    public String name;
    private int age;
    public int getAge() {
        return age;
    }
}
```