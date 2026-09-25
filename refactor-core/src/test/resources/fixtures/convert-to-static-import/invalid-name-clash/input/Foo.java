import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static java.util.Arrays.sort;

public class Foo {
    public void process(List<Integer> data, int[] arr) {
        sort(arr);
        Collections.sort(data);
    }
}
