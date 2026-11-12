# Aggressive inline: pattern, for, and catch variables


### Input:
```java
public class Foo {
    public int run() {
        String s = "";
        RuntimeException e = new RuntimeException("x");
        int total = total("ab,cd");
        return total + s.length() + e.getMessage().length();
    }

    private int total(String item) {
        int count = item.length();
        for (String s : item.split(",")) {
            count += s.length();
        }
        Object value = item;
        if (value instanceof String s) {
            count += s.length();
        }
        try {
            throw new RuntimeException(item);
        } catch (RuntimeException e) {
            count += e.getMessage().length();
        }
        return count;
    }
}
```

### Refactoring:
**inline method** `total("ab,cd")` at line 5, col 21  
colliding pattern, enhanced-for, and catch variables are renamed by binding

### Output:
```java
public class Foo {
    public int run() {
        String s = "";
        RuntimeException e = new RuntimeException("x");
        String __inline_item = "ab,cd";
		int __inline_result;
		__inline_label: {int count=__inline_item.length();for (String __inline_s:__inline_item.split(",")){count+=__inline_s.length();}Object value=__inline_item;if (value instanceof String __inline_s1){count+=__inline_s1.length();}try  {throw new RuntimeException(__inline_item);} catch (RuntimeException __inline_e){count+=__inline_e.getMessage().length();}{__inline_result=count;break __inline_label;}}
		int total = __inline_result;
        return total + s.length() + e.getMessage().length();
    }

    private int total(String item) {
        int count = item.length();
        for (String s : item.split(",")) {
            count += s.length();
        }
        Object value = item;
        if (value instanceof String s) {
            count += s.length();
        }
        try {
            throw new RuntimeException(item);
        } catch (RuntimeException e) {
            count += e.getMessage().length();
        }
        return count;
    }
}
```