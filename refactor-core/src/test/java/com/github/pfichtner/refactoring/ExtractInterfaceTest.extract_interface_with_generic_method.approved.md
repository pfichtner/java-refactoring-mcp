# Extract interface: Transformable from Converter (generics)


### Input: Converter.java:
```java
public class Converter {
    public <T> T identity(T value) {
        return value;
    }

    public String stringify(Object obj) {
        return obj.toString();
    }
}
```

### Refactoring:
**extract interface** `Converter` → implements `Transformable`  
includes generic method <T> T identity(T value)

### Output: Converter.java:
```java
public class Converter implements Transformable {
    public <T> T identity(T value) {
        return value;
    }

    public String stringify(Object obj) {
        return obj.toString();
    }
}
```

### New: Transformable.java:
```java
public interface Transformable {
    <T> T identity(T value);
    String stringify(Object obj);
}
```