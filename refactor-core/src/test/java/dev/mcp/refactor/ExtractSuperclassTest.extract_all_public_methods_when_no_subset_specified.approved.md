# Extract superclass: BaseAnimal (all public methods)


### Input: Animal.java:
```java
public class Animal {
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

### Refactoring:
**extract superclass** `Animal` → extends `BaseAnimal`  
all public methods moved: breathe, eat, name

### Output: Animal.java:
```java
public class Animal extends BaseAnimal {


}
```

### New: BaseAnimal.java:
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