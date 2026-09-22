# Convert anonymous Runnable → nested class Worker


### Input:
```java
public class Outer {

    void start() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println("running");
            }
        };
        r.run();
    }
}
```

### Refactoring:
**convert anonymous to nested** `new Runnable() { ... }` → `new Worker()`; add `private class Worker implements Runnable`  
line 4, col 22

### Output:
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