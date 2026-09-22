# extract-superclass


### Command:
```
extract-superclass --file {root}/Animal.java --name BaseAnimal --superclass-file {root}/BaseAnimal.java
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### Animal.java:
```java
public class Animal extends BaseAnimal {


}
```

### BaseAnimal.java:
```java
public abstract class BaseAnimal {
    public void breathe() {
        System.out.println("breathing");
    }
    public void eat() {
        System.out.println("eating");
    }
    public String name() {
        return "Animal";
    }
}
```