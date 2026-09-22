# convert-nested


### Command:
```
convert-nested --file {root}/Outer.java --line 15 --column 25 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.

=== Outer.java (modified) ===
package com.example;

public class Outer {

    private String name;

    public Outer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

=== Helper.java (new) ===
package com.example;

public class Helper {

    static int doubleIt(int x) {
        return x * 2;
    }
}
```