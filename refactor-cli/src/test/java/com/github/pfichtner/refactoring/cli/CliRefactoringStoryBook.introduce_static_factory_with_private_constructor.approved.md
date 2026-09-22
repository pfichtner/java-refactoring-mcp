# introduce-factory


### Command:
```
introduce-factory --file {root}/src/main/java/com/example/Counter.java --line 7 --column 5 --name of --private-constructor --dry-run
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
        Counter c1 = Counter.of(0, "start");
        Counter c2 = Counter.of(42, "answer");
        System.out.println(c1.label() + ": " + c1.value());
        System.out.println(c2.label() + ": " + c2.value());
    }
}

=== Counter.java ===
package com.example;

public class Counter {
    private final int value;
    private final String label;

    private Counter(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static Counter of(int value, String label) {
        return new Counter(value, label);
    }

    public int value() {
        return value;
    }

    public String label() {
        return label;
    }
}
```