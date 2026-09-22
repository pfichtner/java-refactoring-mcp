# introduce-indirection


### Command:
```
introduce-indirection --file {root}/MathUtils.java --line 3 --column 26 --name computeSquare --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no file written.

public class MathUtils {

    public static int square(int x) {
        return x * x;
    }


    public static int computeSquare(int x) {
        return square(x);
    }
}
```