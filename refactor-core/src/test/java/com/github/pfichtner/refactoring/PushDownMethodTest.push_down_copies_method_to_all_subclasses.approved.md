# Push down method: Shape.area → Circle, Rectangle


### Input: Circle.java:
```java
package com.example;

public class Circle extends Shape {
}
```

### Input: Rectangle.java:
```java
package com.example;

public class Rectangle extends Shape {
}
```

### Input: Shape.java:
```java
package com.example;

public class Shape {
    public double area() {
        return 0.0;
    }
}
```

### Refactoring:
**push down method** `Shape.area()` → direct subclasses  
line 4, col 19

### Output: Circle.java:
```java
package com.example;

public class Circle extends Shape {

    public double area() {
        return 0.0;
    }
}
```

### Output: Rectangle.java:
```java
package com.example;

public class Rectangle extends Shape {

    public double area() {
        return 0.0;
    }
}
```

### Output: Shape.java:
```java
package com.example;

public class Shape {
}
```