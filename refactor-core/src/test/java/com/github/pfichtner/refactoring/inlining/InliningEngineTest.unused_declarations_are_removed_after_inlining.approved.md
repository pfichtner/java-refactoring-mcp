# Aggressive inline: unused declaration removed after inlining


### Input:
```java
public class Foo {
    public int run() {
        return helper();
    }

    private int helper() {
        return 42;
    }
}
```

### Refactoring:
**inline all with removal** `helper()`  
helper() is inlined into run() and removed because no call sites remain

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 42;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```