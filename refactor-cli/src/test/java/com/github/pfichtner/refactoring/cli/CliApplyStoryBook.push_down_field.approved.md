# push-down-field


### Command:
```
push-down-field --file {root}/src/main/java/com/example/Vehicle.java --field maxSpeed
```

### Exit code: 0

### Files changed: 3 | Files deleted: 0

### src/main/java/com/example/Car.java:
```java
package com.example;

public class Car extends Vehicle {
    private int doors;
    protected int maxSpeed;

    public int topSpeed() {
        return maxSpeed;
    }
}
```

### src/main/java/com/example/Truck.java:
```java
package com.example;

public class Truck extends Vehicle {
    protected int maxSpeed;

    public boolean canHighway() {
        return maxSpeed >= 80;
    }
}
```

### src/main/java/com/example/Vehicle.java:
```java
package com.example;

public class Vehicle {
    protected String brand;

    public String brand() {
        return brand;
    }
}
```