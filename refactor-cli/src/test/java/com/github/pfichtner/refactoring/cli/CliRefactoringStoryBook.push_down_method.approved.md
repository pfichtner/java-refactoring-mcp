# push-down


### Command:
```
push-down --file {root}/src/main/java/com/example/Shape.java --method area --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (3):

=== Circle.java ===
package com.example;

public class Circle extends Shape {

    public double area() {
        return 0.0;
    }
}

=== Rectangle.java ===
package com.example;

public class Rectangle extends Shape {

    public double area() {
        return 0.0;
    }
}

=== Shape.java ===
package com.example;

public class Shape {
}
```