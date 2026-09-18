# Rename type: Rectangle → Rect (multi-file)


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Rectangle rect = new Rectangle(3, 4);
        System.out.println(rect.area());
    }
}
```

### Input: Rectangle.java:
```java
package com.example;

public class Rectangle {
    private int width;
    private int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int area() {
        return width * height;
    }
}
```

### Refactoring:
**rename type** `Rectangle` → `Rect`  
target: line 3, col 14 in Rectangle.java

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Rect rect = new Rect(3, 4);
        System.out.println(rect.area());
    }
}
```

### Output: Rectangle.java:
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