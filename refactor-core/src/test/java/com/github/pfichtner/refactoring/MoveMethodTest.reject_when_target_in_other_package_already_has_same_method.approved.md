# Move method rejected: com.other.Report already declares format


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

### Refactoring:
**move method** `Printer.format(Report)` → `com.other.Report`  
line 5, col 19

### Diagnostic:
```
Class 'com.other.Report' already declares 'format' with 1 parameter(s).
```