public class Outer {

    void start() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println("running");
            }
        };
        r.run();
    }
}
