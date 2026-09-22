# decompose-conditional


### Command:
```
decompose-conditional --file {root}/Foo.java --line 6 --column 9 --name isAdultPremium
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Foo.java:
```java
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