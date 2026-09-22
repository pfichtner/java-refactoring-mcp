# remove-method


### Command:
```
remove-method --file {root}/src/main/java/com/example/Printable.java --method print
```

### Exit code: 0

### Files changed: 3 | Files deleted: 0

### src/main/java/com/example/Document.java:
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

### src/main/java/com/example/Printable.java:
```java
package com.example;

public interface Printable {
    String describe();
}
```

### src/main/java/com/example/Report.java:
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