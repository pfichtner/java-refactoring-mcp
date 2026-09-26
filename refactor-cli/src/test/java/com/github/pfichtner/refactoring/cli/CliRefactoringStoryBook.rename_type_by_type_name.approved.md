# rename


### Command:
```
rename --file {root}/src/main/java/com/example/Rectangle.java --type Rectangle --name Rect --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== App.java ===
package com.example;

public class App {
    public static void main(String[] args) {
        Rect rect = new Rect(3, 4);
        System.out.println(rect.area());
    }
}

=== Rect.java ===
package com.example;

public class Rect {
    private int width;
    private int height;

    public Rect(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int area() {
        return width * height;
    }
}
```