# Extract superclass: Living from Animal


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
**extract superclass** `Animal` → extends `Living`  
moves breathe() and eat() to abstract superclass; name() stays in Animal

### Output: Animal.java:
```java
public class Animal extends Living {


    public String name() {
        return "Animal";
    }
}
```

### New: Living.java:
```java
public abstract class Living {
    public void breathe() {
        System.out.println("breathing");
    }
    public void eat() {
        System.out.println("eating");
    }
}
```