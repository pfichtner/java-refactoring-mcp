# Push down method by name: Shape.area


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
**push down method** `Shape.area()` → subclasses  
target: method 'area'

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