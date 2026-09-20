public class Foo {
    private int age;
    private boolean premium;

    public String classify() {
        if (age >= 18 && premium) {
            return "adult premium";
        }
        return "other";
    }
}
