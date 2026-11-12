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
