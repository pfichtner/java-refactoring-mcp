# Aggressive inline: call in constructor argument


### Input:
```java
public class Foo {
    public int run() {
        StringBuilder sb = new StringBuilder(capacity());
        sb.append("x");
        return sb.length();
    }

    private int capacity() {
        return 5;
    }
}
```

### Refactoring:
**inline method** `capacity()` at line 3, col 46  
constructor argument; no preceding side-effecting argument; body hoisted before enclosing statement

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 5;
				break __inline_label;
			}
		}
		StringBuilder sb = new StringBuilder(__inline_result);
        sb.append("x");
        return sb.length();
    }

    private int capacity() {
        return 5;
    }
}
```