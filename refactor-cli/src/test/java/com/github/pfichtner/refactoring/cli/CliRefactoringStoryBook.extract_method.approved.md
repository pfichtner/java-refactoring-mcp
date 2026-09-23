# extract


### Command:
```
extract --file {root}/Greeter.java --start-line 3 --start-column 9 --end-line 5 --end-column 9 --name sayHi --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Greeter.java ===
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