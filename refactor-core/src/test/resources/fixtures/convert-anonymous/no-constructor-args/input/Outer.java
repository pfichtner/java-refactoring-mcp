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
