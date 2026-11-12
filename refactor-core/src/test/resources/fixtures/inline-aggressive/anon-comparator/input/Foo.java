import java.util.Comparator;

class Foo {
    Comparator<String> byLength() {
        return new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(a.length(), b.length());
            }
        };
    }
}
