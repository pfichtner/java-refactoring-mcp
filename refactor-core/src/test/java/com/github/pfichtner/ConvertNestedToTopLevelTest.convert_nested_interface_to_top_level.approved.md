# Convert nested interface Transformer → top-level Transformer.java


### Input: Container.java:
```java
package com.example;

public class Container {

    private Object value;

    public interface Transformer {
        Object transform(Object input);
    }

    public Object apply(Transformer t) {
        return t.transform(value);
    }
}
```

### Refactoring:
**convert nested to top-level** `public interface Transformer` → new file `Transformer.java`  
line 7, col 12

### Output: Container.java:
```java
package com.example;

public class Container {

    private Object value;

    public Object apply(Transformer t) {
        return t.transform(value);
    }
}
```

### New file: Transformer.java:
```java
package com.example;

public interface Transformer {
    Object transform(Object input);
}
```