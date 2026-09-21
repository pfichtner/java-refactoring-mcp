# Remove method: print() from interface — cascades to all implementors


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
**remove method** `void print()` in Printable  
line 4, col 5 in Printable.java — cascade=true

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

### Output: Printable.java:
```java
package com.example;

public interface Printable {
    String describe();
}
```

### Output: Report.java:
```java
package com.example;

public class Report extends Document {

    private final String title;

    public Report(String title, String content) {
        super(content);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
```