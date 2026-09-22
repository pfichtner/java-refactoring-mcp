# convert-anonymous


### Command:
```
convert-anonymous --file {root}/Outer.java --line 4 --column 22 --name Worker
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

### Outer.java:
```java
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