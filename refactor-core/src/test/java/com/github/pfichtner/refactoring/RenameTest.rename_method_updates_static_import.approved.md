# Rename method: formatPreview → formatDryrun (updates static import)


### Input: App.java:
```java
package com.example;

import static com.example.Utils.formatPreview;

public class App {
    public static void main(String[] args) {
        System.out.println(formatPreview("hello"));
    }
}
```

### Input: Utils.java:
```java
package com.example;

public class Utils {
    public static String formatPreview(String value) {
        return value;
    }

    public static String formatPreview(String value, String suffix) {
        return value + suffix;
    }
}
```

### Refactoring:
**rename method** `Utils.formatPreview(String)` → `Utils.formatDryrun`  
target: line 4, col 26 in Utils.java

### Output: App.java:
```java
package com.example;

import static com.example.Utils.formatDryrun;

public class App {
    public static void main(String[] args) {
        System.out.println(formatDryrun("hello"));
    }
}
```

### Output: Utils.java:
```java
package com.example;

public class Utils {
    public static String formatDryrun(String value) {
        return value;
    }

    public static String formatPreview(String value, String suffix) {
        return value + suffix;
    }
}
```