# extract-superclass


### Command:
```
extract-superclass --file {root}/Animal.java --name BaseAnimal --output-file {root}/BaseAnimal.java --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== Animal.java ===
public class Animal extends BaseAnimal {


}

=== BaseAnimal.java ===
public abstract class BaseAnimal {
    public void breathe() {
        System.out.println("breathing");
    }
    public void eat() {
        System.out.println("eating");
    }
    public String name() {
        return "Animal";
    }
}
```