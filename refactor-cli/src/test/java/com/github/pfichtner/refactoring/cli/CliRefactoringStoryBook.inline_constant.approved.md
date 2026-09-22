# inline-const


### Command:
```
inline-const --file {root}/Foo.java --line 5 --column 21 --all-occurrences --remove-declaration --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no file written.

public class Foo {

    public boolean isValid(int x) {
        return x <= 100;
    }

    public int clamp(int x) {
        return Math.min(x, 100);
    }
}
```