# Aggressive inline: cross-type generic explicit receiver without type variable


### Input:
```java
class Box<T> {
    final int size() {
        return 1;
    }
}

public class Foo {
    public int run(Box<String> box) {
        return box.size();
    }
}
```

### Refactoring:
**inline method** `box.size()` at line 9, col 16  
method body does not reference type variable; receiver captured once

### Output:
```java
class Box<T> {
    final int size() {
        return 1;
    }
}

public class Foo {
    public int run(Box<String> box) {
        Box<String> __inline_receiver = box;
		java.util.Objects.requireNonNull(__inline_receiver);
		int __inline_result;
		__inline_label: {
			{
				__inline_result = 1;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```