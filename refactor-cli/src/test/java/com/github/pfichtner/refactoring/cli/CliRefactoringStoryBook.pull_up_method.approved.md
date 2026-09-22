# pull-up


### Command:
```
pull-up --file {root}/src/main/java/com/example/Dog.java --method speak --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== Animal.java ===
package com.example;

public class Animal {
    public String name() {
        return "Animal";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}

=== Dog.java ===
package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }
}
```