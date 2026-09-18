# Rename method in generic class: Container<T>.getValue → get


### Input: Container.java:
```java
package com.example;

public class Container<T> {
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public static <T> Container<T> of(T value) {
        Container<T> c = new Container<>();
        c.setValue(value);
        return c;
    }
}
```

### Refactoring:
**rename method** `Container<T>.getValue()` → `get()`  
type parameter T and factory method preserved; return type unaffected

### Output: Container.java:
```java
package com.example;

public class Container<T> {
    private T value;

    public T get() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public static <T> Container<T> of(T value) {
        Container<T> c = new Container<>();
        c.setValue(value);
        return c;
    }
}
```