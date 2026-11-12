class Foo {
    Runnable make() {
        return new Runnable() {
            @Override
            public void run() {
                System.out.println(this.getClass().getSimpleName());
            }
        };
    }
}
