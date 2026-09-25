public class Foo {
    private int count;
    private int max;

    public void process() {
        while (count < max && max > 0) {
            count++;
        }
    }
}
