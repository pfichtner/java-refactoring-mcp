# inline-var


### Command:
```
inline-var --file {root}/Foo.java --line 3 --column 16 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no file written.

public class Foo {
    public void run() {
        System.out.println("Hello");
        System.err.println("Hello");
    }
}
```