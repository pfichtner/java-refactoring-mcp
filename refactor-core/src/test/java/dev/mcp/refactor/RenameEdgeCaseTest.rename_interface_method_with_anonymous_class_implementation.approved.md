# Rename interface method: Task.execute → perform (anonymous impl)


### Input: Runner.java:
```java
package com.example;

public class Runner {
    interface Task {
        void execute();
    }

    public void run() {
        Task t = new Task() {
            @Override
            public void execute() {
                System.out.println("running");
            }
        };
        t.execute();
    }
}
```

### Refactoring:
**rename method** `Task.execute()` → `perform()`  
interface declaration and call site renamed; KNOWN LIMITATION: anonymous class @Override not renamed (different binding key from the interface method)

### Output: Runner.java:
```java
package com.example;

public class Runner {
    interface Task {
        void perform();
    }

    public void run() {
        Task t = new Task() {
            @Override
            public void execute() {
                System.out.println("running");
            }
        };
        t.perform();
    }
}
```