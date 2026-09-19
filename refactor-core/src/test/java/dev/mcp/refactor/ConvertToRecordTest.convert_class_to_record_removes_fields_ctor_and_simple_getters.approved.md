# Convert class to record: Point


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
**convert to record** `class Point` → `record Point(int x, int y)`  
removes fields, all-args constructor, and simple getters; keeps distanceTo

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