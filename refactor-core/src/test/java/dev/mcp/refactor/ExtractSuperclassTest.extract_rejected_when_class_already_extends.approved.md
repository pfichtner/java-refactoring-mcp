# Extract superclass — rejected: class already extends


### Input: Dog.java:
```java
public class Dog extends Animal {
    public void bark() {
        System.out.println("woof");
    }
}
```

### Refactoring:
**extract superclass** `Dog` → extends `Canine`  
Dog already extends Animal — chaining not supported

### Diagnostic:
```
Cannot extract superclass: 'Dog' already extends 'Animal'. Superclass chaining is not supported.
```