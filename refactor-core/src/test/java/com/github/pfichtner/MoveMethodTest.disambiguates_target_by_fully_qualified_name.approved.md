# Move method: FQN selects com.example.Report over com.other.Report


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

### Input: Report.java (com.example):
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

### Input: Report.java (com.other):
```java
package com.other;

public class Report {

    public String title() {
        return "";
    }

    public String format(com.example.Report r) {
        return r.title();
    }
}
```

### Refactoring:
**move method** `Printer.byline(Report, Author)` → `com.example.Report`  
target: line 13, col 19 in Printer.java

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