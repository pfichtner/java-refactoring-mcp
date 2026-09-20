# Introduce parameter object as record: Printer.print(x,y) → Coordinate


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Printer p = new Printer();
        p.print(10, 20, "A");
        p.print(0, 0, "origin");
    }
}
```

### Input: Printer.java:
```java
package com.example;

public class Printer {
    public void print(int x, int y, String label) {
        System.out.println(label + " at (" + x + ", " + y + ")");
    }
}
```

### Refactoring:
**introduce parameter object (--record)** `int x, int y` → `Coordinate coordinate`  
line 4, col 17

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Printer p = new Printer();
        p.print(new Coordinate(10, 20), "A");
        p.print(new Coordinate(0, 0), "origin");
    }
}
```

### Output: Coordinate.java:
```java
package com.example;

public record Coordinate(int x, int y) {}
```

### Output: Printer.java:
```java
package com.example;

public class Printer {
    public void print(Coordinate coordinate, String label) {
        System.out.println(label + " at (" + coordinate.x() + ", " + coordinate.y() + ")");
    }
}
```