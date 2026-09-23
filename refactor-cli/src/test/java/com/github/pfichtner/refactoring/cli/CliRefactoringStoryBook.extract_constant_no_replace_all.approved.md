# extract-const


### Command:
```
extract-const --file {root}/Foo.java --start-line 3 --start-column 28 --end-line 3 --end-column 35 --name GREETING --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Foo.java ===
public class Foo {
    private static final String GREETING = "HELLO";
    public void run() {
        System.out.println(GREETING);
        System.err.println("HELLO");
        String msg = "HELLO" + "!";
    }
}
```