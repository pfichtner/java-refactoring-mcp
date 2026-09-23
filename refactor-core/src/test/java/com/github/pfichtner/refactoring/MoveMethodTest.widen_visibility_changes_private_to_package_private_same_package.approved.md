# Move method with widen_visibility (same package): private → package-private


### Input: Printer.java:
```java
package com.example;

public class Printer {
    private String format(Report report) {
        return "Report: " + report.title();
    }
}
```

### Input: Report.java:
```java
package com.example;

public class Report {
    private final String title;

    public Report(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
```

### Refactoring:
**move method** `Printer.format(Report)` → `com.example.Report` (widen_visibility=true, same package → package-private)  
line 4, col 20

### Output: Printer.java:
```java
package com.example;

public class Printer {
}
```

### Output: Report.java:
```java
package com.example;

public class Report {
    private final String title;

    public Report(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    String format(Report report) {
        return "Report: " + report.title();
    }
}
```