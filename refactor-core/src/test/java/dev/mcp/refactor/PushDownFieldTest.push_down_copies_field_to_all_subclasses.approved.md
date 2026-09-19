# Push down field: Vehicle.maxSpeed → Car, Truck


### Input: Car.java:
```java
package com.example;

public class Car extends Vehicle {
    private int doors;

    public int topSpeed() {
        return maxSpeed;
    }
}
```

### Input: Truck.java:
```java
package com.example;

public class Truck extends Vehicle {
    public boolean canHighway() {
        return maxSpeed >= 80;
    }
}
```

### Input: Vehicle.java:
```java
package com.example;

public class Vehicle {
    protected int maxSpeed;
    protected String brand;

    public String brand() {
        return brand;
    }
}
```

### Refactoring:
**push down field** `Vehicle.maxSpeed` → subclasses  
line 4, col 19

### Output: Car.java:
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

### Output: Truck.java:
```java
package com.example;

public class Truck extends Vehicle {
    protected int maxSpeed;

    public boolean canHighway() {
        return maxSpeed >= 80;
    }
}
```

### Output: Vehicle.java:
```java
package com.example;

public class Vehicle {
    protected String brand;

    public String brand() {
        return brand;
    }
}
```