# remove-param


### Command:
```
remove-param --file {root}/src/main/java/com/example/Computation.java --method add --parameter c --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.

=== App.java ===
package com.example;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        int result = calc.add(1, 2);
        System.out.println(result);
    }
}

=== Computation.java ===
package com.example;

public class Computation {
    public int add(int a, int b) {
        return a + b;
    }
}
```