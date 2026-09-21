# Remove method: print() from Document only — cascade=false


### Input: Document.java:
```java
package com.example;

public class Document implements Printable {

    private final String content;

    public Document(String content) {
        this.content = content;
    }

    @Override
    public void print() {
        System.out.println(content);
    }

    @Override
    public String describe() {
        return "Document: " + content;
    }

    public String getContent() {
        return content;
    }
}
```

### Input: Printable.java:
```java
package com.example;

public interface Printable {
    void print();

    String describe();
}
```

### Input: Report.java:
```java
package com.example;

public class Report extends Document {

    private final String title;

    public Report(String title, String content) {
        super(content);
        this.title = title;
    }

    @Override
    public void print() {
        System.out.println(title + ": " + getContent());
    }

    public String getTitle() {
        return title;
    }
}
```

### Refactoring:
**remove method** `void print()` in Document  
line 12, col 12 in Document.java — cascade=false, Report's override kept

### Output: Document.java:
```java
package com.example;

public class Document implements Printable {

    private final String content;

    public Document(String content) {
        this.content = content;
    }

    @Override
    public String describe() {
        return "Document: " + content;
    }

    public String getContent() {
        return content;
    }
}
```