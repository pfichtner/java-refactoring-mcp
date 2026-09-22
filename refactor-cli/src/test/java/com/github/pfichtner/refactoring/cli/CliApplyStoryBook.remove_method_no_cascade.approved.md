# remove-method


### Command:
```
remove-method --file {root}/src/main/java/com/example/Document.java --method print --no-cascade
```

### Exit code: 0

### Files changed: 1 | Files deleted: 0

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