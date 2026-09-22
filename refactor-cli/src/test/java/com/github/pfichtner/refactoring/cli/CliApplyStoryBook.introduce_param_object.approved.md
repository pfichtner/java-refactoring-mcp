# introduce-param-object


### Command:
```
introduce-param-object --file {root}/src/main/java/com/example/Printer.java --line 4 --column 17 --params x,y --class-name Coordinate --record
```

### Exit code: 0

### Files changed: 3 | Files deleted: 0

### src/main/java/com/example/App.java:
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

### src/main/java/com/example/Coordinate.java:
```java
package com.example;

public record Coordinate(int x, int y) {}
```

### src/main/java/com/example/Printer.java:
```java
package com.example;

public class Printer {
    public void print(Coordinate coordinate, String label) {
        System.out.println(label + " at (" + coordinate.x() + ", " + coordinate.y() + ")");
    }
}
```