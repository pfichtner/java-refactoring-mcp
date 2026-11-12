# Aggressive inline: overload and explicit this receiver


### Input:
```java
public class Foo {
    public int run() {
        return this.pick(3);
    }

    private int pick(int value) {
        return value + 10;
    }

    private String pick(String value) {
        return value;
    }
}
```

### Refactoring:
**inline method** `this.pick(3)` at line 3, col 16  
binding key selects the int overload; explicit this receiver is accepted

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 3 + 10;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int pick(int value) {
        return value + 10;
    }

    private String pick(String value) {
        return value;
    }
}
```