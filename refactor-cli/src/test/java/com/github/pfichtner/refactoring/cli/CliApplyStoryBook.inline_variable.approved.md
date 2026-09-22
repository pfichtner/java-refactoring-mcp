# inline-var


### Command:
```
inline-var --file {root}/Foo.java --line 3 --column 16
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.err.println("Hello");
    }
}
```