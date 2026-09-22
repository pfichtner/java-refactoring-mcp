# Move method to named target: Printer.byline(Report, Author) → com.example.Report


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

    public String byline(Report report, Author author) {
        return report.title() + " by " + author.name();
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
**move method** `Printer.byline(Report, Author)` → `com.example.Report` (not Author)  
target class named explicitly by FQN; line 13, col 19 in Printer.java

### Output: Printer.java:
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

    public String byline(Report report, Author author) {
        return report.title() + " by " + author.name();
    }
}
```