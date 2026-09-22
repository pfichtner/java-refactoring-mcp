# Convert class to record: Point (getters renamed at call sites)


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p.getX());
        System.out.println(p.getY());
        Point origin = new Point(0, 0);
        System.out.println(p.distanceTo(origin));
    }
}
```

### Input: Point.java:
```java
package com.example;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    public double distanceTo(Point other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```

### Refactoring:
**convert to record** `class Point` → `record Point(int x, int y)`; `getX()`→`x()`, `getY()`→`y()`  
fields, constructor, and bean getters removed; App.java call sites renamed

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p.x());
        System.out.println(p.y());
        Point origin = new Point(0, 0);
        System.out.println(p.distanceTo(origin));
    }
}
```

### Output: Point.java:
```java
package com.example;

public record Point(int x, int y) {

    public double distanceTo(Point other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```