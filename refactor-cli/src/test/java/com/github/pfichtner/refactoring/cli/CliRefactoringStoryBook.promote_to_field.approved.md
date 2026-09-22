# promote-to-field


### Command:
```
promote-to-field --file {root}/Counter.java --line 4 --column 15 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no file written.

public class Counter {
    private int count;

    void reset() {
        count = 0;
        System.out.println(count);
    }
}
```