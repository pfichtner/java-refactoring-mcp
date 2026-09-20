# Convert anonymous Comparator → nested class CaseInsensitiveOrder


### Input:
```java
import java.util.Comparator;

public class Outer {

    java.util.List<String> sorted;

    void init(java.util.List<String> list) {
        list.sort(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        this.sorted = list;
    }
}
```

### Refactoring:
**convert anonymous to nested** `new Comparator<String>() { ... }` → `new CaseInsensitiveOrder()`  
line 8, col 19

### Output:
```java
import java.util.Comparator;

public class Outer {

    java.util.List<String> sorted;

    void init(java.util.List<String> list) {
        list.sort(new CaseInsensitiveOrder());
        this.sorted = list;
    }


    private class CaseInsensitiveOrder implements Comparator<String> {

        @Override
        public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
        }
    }
}
```