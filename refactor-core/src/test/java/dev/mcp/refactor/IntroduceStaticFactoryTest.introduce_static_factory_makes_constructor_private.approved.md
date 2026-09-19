# Introduce static factory: Counter.create (constructor private)


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Counter c1 = new Counter(0, "start");
        Counter c2 = new Counter(42, "answer");
        System.out.println(c1.label() + ": " + c1.value());
        System.out.println(c2.label() + ": " + c2.value());
    }
}
```

### Input: Counter.java:
```java
package com.example;

public class Counter {
    private final int value;
    private final String label;

    public Counter(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int value() {
        return value;
    }

    public String label() {
        return label;
    }
}
```

### Refactoring:
**introduce static factory** `Counter(int,String)` → `Counter.create` + private constructor  
line 7, col 12

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Counter c1 = Counter.create(0, "start");
        Counter c2 = Counter.create(42, "answer");
        System.out.println(c1.label() + ": " + c1.value());
        System.out.println(c2.label() + ": " + c2.value());
    }
}
```

### Output: Counter.java:
```java
package com.example;

public class Counter {
    private final int value;
    private final String label;

    private Counter(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static Counter create(int value, String label) {
        return new Counter(value, label);
    }

    public int value() {
        return value;
    }

    public String label() {
        return label;
    }
}
```