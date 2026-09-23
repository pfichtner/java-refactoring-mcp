# introduce-param


### Command:
```
introduce-param --file {root}/src/main/java/com/example/Greeter.java --start-line 5 --start-column 40 --end-line 5 --end-column 47 --name whom --dry-run
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
        Greeter g = new Greeter();
        g.greet("World");
    }
}

=== Greeter.java ===
package com.example;

public class Greeter {
    public void greet(java.lang.String whom) {
        System.out.println("Hello, " + whom);
    }
}
```