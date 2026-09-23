# convert-anonymous


### Command:
```
convert-anonymous --file {root}/Outer.java --line 4 --column 22 --name Worker --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (1):

=== Outer.java ===
public class Outer {

    void start() {
        Runnable r = new Worker();
        r.run();
    }


    private class Worker implements Runnable {

        @Override
        public void run() {
            System.out.println("running");
        }
    }
}
```