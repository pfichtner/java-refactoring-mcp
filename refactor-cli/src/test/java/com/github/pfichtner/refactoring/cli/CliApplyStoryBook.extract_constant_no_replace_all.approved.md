# extract-const


### Command:
```
extract-const --file {root}/Foo.java --start-line 3 --start-column 28 --end-line 3 --end-column 35 --name GREETING
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
public class Foo {
    private static final String GREETING = "HELLO";
    public void run() {
        System.out.println(GREETING);
        System.err.println("HELLO");
        String msg = "HELLO" + "!";
    }
}
```