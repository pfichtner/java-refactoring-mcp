# decompose-conditional


### Command:
```
decompose-conditional --file {root}/Foo.java --line 6 --column 9 --name isAdultPremium --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no file written.

=== Foo.java ===
public class Foo {
    private int age;
    private boolean premium;

    public String classify() {
        if (isAdultPremium()) {
            return "adult premium";
        }
        return "other";
    }


    private boolean isAdultPremium() {
        return age >= 18 && premium;
    }
}
```