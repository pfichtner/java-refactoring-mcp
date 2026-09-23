# convert-nested


### Command:
```
convert-nested --file {root}/Outer.java --line 15 --column 25 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== Helper.java ===
package com.example;

public class Helper {

    static int doubleIt(int x) {
        return x * 2;
    }
}

=== Outer.java ===
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
```