# Rename field by name: name → fullName


### Input: Person.java:
```java
package com.example;

public class Person {
    public String name;

    public Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

### Input: Printer.java:
```java
package com.example;

public class Printer {
    public void print(Person person) {
        System.out.println("Name: " + person.name);
    }
}
```

### Refactoring:
**rename field** `Person.name` → `Person.fullName`  
target: field 'name'

### Output: Person.java:
```java
package com.example;

public class Person {
    public String fullName;

    public Person(String name) {
        this.fullName = name;
    }

    public String getName() {
        return fullName;
    }
}
```

### Output: Printer.java:
```java
package com.example;

public class Printer {
    public void print(Person person) {
        System.out.println("Name: " + person.fullName);
    }
}
```