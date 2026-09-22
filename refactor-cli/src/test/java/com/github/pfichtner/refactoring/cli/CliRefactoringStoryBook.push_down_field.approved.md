# push-down-field


### Command:
```
push-down-field --file {root}/src/main/java/com/example/Vehicle.java --field maxSpeed --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (3):

=== Car.java ===
package com.example;

public class Car extends Vehicle {
    private int doors;
    protected int maxSpeed;

    public int topSpeed() {
        return maxSpeed;
    }
}

=== Truck.java ===
package com.example;

public class Truck extends Vehicle {
    protected int maxSpeed;

    public boolean canHighway() {
        return maxSpeed >= 80;
    }
}

=== Vehicle.java ===
package com.example;

public class Vehicle {
    protected String brand;

    public String brand() {
        return brand;
    }
}
```