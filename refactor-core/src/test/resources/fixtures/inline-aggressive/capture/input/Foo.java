public class Foo {
    public int run() {
        int result = 100;
        int picked = pick(5);
        return picked + result;
    }

    private int pick(int value) {
        int result = value + 1;
        Runnable lambda = () -> System.out.println(result + value);
        Runnable anonymous = new Runnable() {
            public void run() {
                System.out.println(result + value);
            }
        };
        class Inner {
            int sum() {
                return result + value;
            }
        }
        lambda.run();
        anonymous.run();
        return new Inner().sum();
    }
}
