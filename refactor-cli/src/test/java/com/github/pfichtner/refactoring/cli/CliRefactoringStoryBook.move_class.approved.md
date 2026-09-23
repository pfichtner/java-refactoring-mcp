# move-class


### Command:
```
move-class --file {root}/src/main/java/com/example/service/Calculator.java --package com.example.util --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== App.java ===
package com.example.app;

import com.example.util.Calculator;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println(calc.add(1, 2));
    }
}

=== Calculator.java ===
package com.example.util;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```