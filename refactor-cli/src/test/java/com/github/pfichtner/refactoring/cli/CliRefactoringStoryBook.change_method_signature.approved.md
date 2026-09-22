# change-method-signature


### Command:
```
change-method-signature --file {root}/src/main/java/com/example/Converter.java --method convert --param-order 1,0 --return-type Object --dry-run
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
        Converter c = new Converter();
        System.out.println(c.convert("val:", 42));
        System.out.println(c.convert("id:", 7));
    }
}

=== Converter.java ===
package com.example;

public class Converter {
    public Object convert(String prefix, int value) {
        return prefix + value;
    }
}
```