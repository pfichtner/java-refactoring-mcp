class Foo {
    Runnable make() {
        return new Runnable() {
            private int count = 0;

            @Override
            public void run() {
                count++;
            }
        };
    }
}
