# convert-to-record


### Command:
```
convert-to-record --file {root}/src/main/java/com/example/Point.java
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/App.java:
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

### src/main/java/com/example/Point.java:
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