# encapsulate-field


### Command:
```
encapsulate-field --file {root}/src/main/java/com/example/Person.java --field name --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== App.java ===
package com.example;

public class App {
    public static void main(String[] args) {
        Person p = new Person();
        p.name = "Alice";
        System.out.println(p.getName());
        p.age = 30;
    }
}

=== Person.java ===
package com.example;

public class Person {
    private String name;
    public String getName() {
        return name;
    }
    public int age;
}
```