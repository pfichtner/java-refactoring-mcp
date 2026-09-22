# encapsulate-field


### Command:
```
encapsulate-field --file {root}/src/main/java/com/example/Person.java --field name
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/App.java:
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

### src/main/java/com/example/Person.java:
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