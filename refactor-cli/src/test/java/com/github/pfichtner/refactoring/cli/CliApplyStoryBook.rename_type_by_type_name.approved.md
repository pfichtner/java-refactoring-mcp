# rename


### Command:
```
rename --file {root}/src/main/java/com/example/Rectangle.java --type Rectangle --name Rect
```

### Exit code: 0

### Files changed: 2 | Files deleted: 1

### src/main/java/com/example/App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Rect rect = new Rect(3, 4);
        System.out.println(rect.area());
    }
}
```

### src/main/java/com/example/Rect.java:
```java
package com.example;

public class Rect {
    private int width;
    private int height;

    public Rect(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int area() {
        return width * height;
    }
}
```

### src/main/java/com/example/Rectangle.java [deleted]