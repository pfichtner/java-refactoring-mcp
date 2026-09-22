# Introduce parameter object: Printer.print(x,y) → Coordinate


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
**introduce parameter object** `int x, int y` → `Coordinate coordinate`  
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

public class Coordinate {
    private final int x;
    private final int y;

    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
```

### Output: Printer.java:
```java
package com.example;

public class Printer {
    public void print(Coordinate coordinate, String label) {
        System.out.println(label + " at (" + coordinate.getX() + ", " + coordinate.getY() + ")");
    }
}
```