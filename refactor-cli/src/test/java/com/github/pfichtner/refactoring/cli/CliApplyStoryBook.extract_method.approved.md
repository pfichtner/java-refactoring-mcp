# extract


### Command:
```
extract --file {root}/Greeter.java --start-line 3 --start-column 9 --end-line 5 --end-column 9 --name sayHi
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Greeter.java:
```java
public class Greeter {
    public void run() {
        sayHi();
        int x = 42;
    }

    private void sayHi() {
        System.out.println("Hello");
        System.out.println("World");
    }
}
```