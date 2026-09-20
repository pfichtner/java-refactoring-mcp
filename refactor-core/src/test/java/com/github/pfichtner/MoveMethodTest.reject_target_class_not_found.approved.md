# Move method rejected: target class not found


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
**move method** `Printer.format(Report)` → `NonExistent`  
line 5, col 19

### Diagnostic:
```
Source file for class 'NonExistent' not found in project source roots.
```