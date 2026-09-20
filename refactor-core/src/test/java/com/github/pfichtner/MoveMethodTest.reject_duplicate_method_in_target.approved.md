# Move method rejected: Report already declares describe(Report)


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

### Refactoring:
**move method** `Printer.describe(Report)` → `Report`  
line 9, col 19

### Diagnostic:
```
Class 'Report' already declares 'describe' with 1 parameter(s).
```