# move-method


### Command:
```
move-method --file {root}/src/main/java/com/example/Printer.java --method format --target-class com.example.Report
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/Printer.java:
```java
package com.example;

public class Printer {

    public String describe(Report report) {
        return "Length: " + report.title().length();
    }

    public String byline(Report report, Author author) {
        return report.title() + " by " + author.name();
    }
}
```

### src/main/java/com/example/Report.java:
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