# extract-superclass


### Command:
```
extract-superclass --file {root}/Animal.java --name BaseAnimal --output-file {root}/BaseAnimal.java --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.

=== Animal.java (modified) ===
public class Animal extends BaseAnimal {


}

=== BaseAnimal.java (new) ===
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