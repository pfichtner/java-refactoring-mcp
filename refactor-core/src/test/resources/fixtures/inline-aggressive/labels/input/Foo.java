public class Foo {
    public int run() {
        int n = 5;
        __inline_label: {
            if (n > 0) {
                return twice(n);
            }
        }
        return 0;
    }

    private int twice(int value) {
        int __inline_result = value;
        __inline_label: {
            if (__inline_result > 10) {
                return __inline_result;
            }
        }
        return __inline_result * 2;
    }
}
