# Rename type via FQN reference: Rectangle → Rect


### Input: App.java:
```java
package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.service.Rectangle rect = new com.example.service.Rectangle(3, 4);
        System.out.println(rect.area());
    }
}
```

### Input: Rectangle.java:
```java
package com.example.service;

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
**rename type** `com.example.service.Rectangle` → `com.example.service.Rect`  
target: line 3, col 14 in Rectangle.java

### Output: App.java:
```java
package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.service.Rect rect = new com.example.service.Rect(3, 4);
        System.out.println(rect.area());
    }
}
```

### Output: Rectangle.java:
```java
package com.example.service;

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