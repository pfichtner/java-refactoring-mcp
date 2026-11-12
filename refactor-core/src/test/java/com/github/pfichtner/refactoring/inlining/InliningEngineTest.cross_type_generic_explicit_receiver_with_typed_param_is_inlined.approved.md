# Aggressive inline: cross-type generic explicit receiver with typed parameter


### Input:
```java
class Box<T> {
    T item;
    final void set(T value) {
        this.item = value;
    }
}

public class Foo {
    public void run(Box<String> box, String value) {
        box.set(value);
    }
}
```

### Refactoring:
**inline method** `box.set(value)` at line 10, col 9  
type variable T substituted with String; receiver captured once

### Output:
```java
class Box<T> {
    T item;
    final void set(T value) {
        this.item = value;
    }
}

public class Foo {
    public void run(Box<String> box, String value) {
        {
			Box<String> __inline_receiver = box;
			java.util.Objects.requireNonNull(__inline_receiver);
			{
				__inline_receiver.item = value;
			}
		}
    }
}
```