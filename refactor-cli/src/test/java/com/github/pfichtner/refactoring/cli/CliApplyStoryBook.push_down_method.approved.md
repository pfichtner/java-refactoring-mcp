# push-down


### Command:
```
push-down --file {root}/src/main/java/com/example/Shape.java --method area
```

### Exit code: 0

### Files changed: 3 | Files deleted: 0

### src/main/java/com/example/Circle.java:
```java
package com.example;

public class Circle extends Shape {

    public double area() {
        return 0.0;
    }
}
```

### src/main/java/com/example/Rectangle.java:
```java
package com.example;

public class Rectangle extends Shape {

    public double area() {
        return 0.0;
    }
}
```

### src/main/java/com/example/Shape.java:
```java
package com.example;

public class Shape {
}
```