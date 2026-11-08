# Aggressive inline: argument captured by a lambda


### Input:
```java
import java.util.function.IntSupplier;

public class Foo {
    public int run() {
        int value = 5;
        return plusOneLater(value);
    }

    private int plusOneLater(int value) {
        IntSupplier supplier = () -> value + 1;
        return supplier.getAsInt();
    }
}
```

### Refactoring:
**inline method** `plusOneLater(value)` at line 6, col 16  
the single use is captured by a lambda, so the argument stays a captured temporary

### Output:
```java
import java.util.function.IntSupplier;

public class Foo {
    public int run() {
        int value = 5;
        int __inline_value = value;
		int __inline_result;
		__inline_label: {
			IntSupplier supplier = () -> __inline_value + 1;
			{
				__inline_result = supplier.getAsInt();
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int plusOneLater(int value) {
        IntSupplier supplier = () -> value + 1;
        return supplier.getAsInt();
    }
}
```