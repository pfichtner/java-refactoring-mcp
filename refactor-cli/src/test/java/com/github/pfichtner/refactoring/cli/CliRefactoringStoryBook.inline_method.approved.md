# inline-method


### Command:
```
inline-method --file {root}/src/main/java/com/example/App.java --line 6 --column 27 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.

=== App.java ===
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = 1 + 2;
        System.out.println(result);
    }
}
```