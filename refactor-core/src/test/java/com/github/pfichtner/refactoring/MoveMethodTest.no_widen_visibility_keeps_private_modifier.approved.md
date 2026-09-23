# Move method without widen_visibility: private modifier preserved


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
**move method** `Printer.format(Report)` → `com.example.Report` (widen_visibility=false)  
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

    private String format(Report report) {
        return "Report: " + report.title();
    }
}
```