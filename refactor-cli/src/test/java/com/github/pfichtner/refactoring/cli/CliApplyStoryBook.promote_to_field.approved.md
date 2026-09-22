# promote-to-field


### Command:
```
promote-to-field --file {root}/Counter.java --line 4 --column 15
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Counter.java:
```java
public class Counter {
    private int count;

    void reset() {
        count = 0;
        System.out.println(count);
    }
}
```