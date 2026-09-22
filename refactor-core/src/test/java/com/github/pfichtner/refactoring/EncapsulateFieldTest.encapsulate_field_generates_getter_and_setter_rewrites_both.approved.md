# Encapsulate field: Person.name — getter + setter


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Person p = new Person();
        p.name = "Alice";
        System.out.println(p.name);
        p.age = 30;
    }
}
```

### Input: Person.java:
```java
package com.example;

public class Person {
    public String name;
    public int age;
}
```

### Refactoring:
**encapsulate field** `public String name` → `private String name` + `getName()` + `setName(String)`  
read sites → getName(); write sites → setName(value)

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Person p = new Person();
        p.setName("Alice");
        System.out.println(p.getName());
        p.age = 30;
    }
}
```

### Output: Person.java:
```java
package com.example;

public class Person {
    private String name;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public int age;
}
```