# remove-method


### Command:
```
remove-method --file {root}/src/main/java/com/example/Printable.java --method print --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (3):

=== Document.java ===
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

=== Printable.java ===
package com.example;

public interface Printable {
    String describe();
}

=== Report.java ===
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