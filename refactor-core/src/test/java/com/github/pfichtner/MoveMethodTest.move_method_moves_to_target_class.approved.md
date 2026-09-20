# Move method: Printer.format → Report


### Input: Printer.java:
```java
package com.example;

public class Printer {

    public String format(Report report) {
        return "Report: " + report.title();
    }

    public String describe(Report report) {
        return "Length: " + report.title().length();
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

    public String describe(Report report) {
        return "Length: " + report.title().length();
    }
}
```

### Refactoring:
**move method** `Printer.format(Report)` → `Report`  
target: line 5, col 19 in Printer.java

### Output: Printer.java:
```java
package com.example;

public class Printer {

    public String describe(Report report) {
        return "Length: " + report.title().length();
    }
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

    public String describe(Report report) {
        return "Length: " + report.title().length();
    }

    public String format(Report report) {
        return "Report: " + report.title();
    }
}
```