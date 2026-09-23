# extract-var


### Command:
```
extract-var --file {root}/Foo.java --start-line 3 --start-column 16 --end-line 3 --end-column 21 --name answer --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Foo.java ===
public class Foo {
    public int compute() {
        int answer = 6 * 7;
        return answer;
    }
}
```