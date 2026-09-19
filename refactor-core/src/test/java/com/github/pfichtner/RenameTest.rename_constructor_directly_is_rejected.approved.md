# Rename constructor directly — expect rejection


### Input:
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
**rename constructor** `Rectangle` → `Rect`  
target: constructor declaration at line 7, col 12

### Diagnostic:
```
Target at offset 106 is a constructor. Rename the type instead.
```