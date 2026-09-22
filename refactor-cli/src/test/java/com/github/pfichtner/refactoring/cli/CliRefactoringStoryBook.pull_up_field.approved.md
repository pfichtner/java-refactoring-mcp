# pull-up-field


### Command:
```
pull-up-field --file {root}/src/main/java/com/example/Dog.java --field breed --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== Animal.java ===
package com.example;

public class Animal {
    protected String name;
    protected String breed;

    public String name() {
        return name;
    }
}

=== Dog.java ===
package com.example;

public class Dog extends Animal {
    protected String name;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
```