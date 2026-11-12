# Aggressive inline: cross-type generic explicit receiver with typed return


### Input:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class Foo {
    public Object run(Box<String> box) {
        return box.get();
    }
}
```

### Refactoring:
**inline method** `box.get()` at line 10, col 16  
type variable T substituted with String; receiver captured once

### Output:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class Foo {
    public Object run(Box<String> box) {
        Box<String> __inline_receiver = box;
		java.util.Objects.requireNonNull(__inline_receiver);
		String __inline_result;
		__inline_label: {
			{
				__inline_result = __inline_receiver.item;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```