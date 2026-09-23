# inline-var


### Command:
```
inline-var --file {root}/Foo.java --line 3 --column 16 --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Foo.java ===
public class Foo {
    public void run() {
        System.out.println("Hello");
        System.err.println("Hello");
    }
}
```