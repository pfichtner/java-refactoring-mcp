# Aggressive inline: call as switch selector


### Input:
```java
public class Foo {
    public int run() {
        switch (compute()) {
            case 1: return 10;
            case 2: return 20;
            default: return 0;
        }
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 17  
switch selector; body hoisted before switch statement, selector replaced with result

### Output:
```java
public class Foo {
    public int run() {
        int __inline_result;
		__inline_label: {
			int x = 1;
			{
				__inline_result = x;
				break __inline_label;
			}
		}
		switch (__inline_result) {
            case 1: return 10;
            case 2: return 20;
            default: return 0;
        }
    }

    private int compute() {
        int x = 1;
        return x;
    }
}
```