# Encapsulate field: Person.name — getter only


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
**encapsulate field** `public String name` → `private String name` + `getName()`  
read sites rewritten to getName(); write sites unchanged (no setter)

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Person p = new Person();
        p.name = "Alice";
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
    public int age;
}
```