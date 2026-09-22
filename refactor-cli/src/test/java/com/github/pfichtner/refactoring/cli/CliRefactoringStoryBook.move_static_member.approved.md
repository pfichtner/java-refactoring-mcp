# move-static


### Command:
```
move-static --file {root}/src/main/java/com/example/MathUtils.java --line 5 --column 5 --target com.example.Helpers --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (3):

=== Client.java ===
package com.example;

public class Client {

    public int compute() {
        return Helpers.square(5);
    }
}

=== Helpers.java ===
package com.example;

public class Helpers {

    public static int square(int x) {
        return x * x;
    }
}

=== MathUtils.java ===
package com.example;

public class MathUtils {
}
```