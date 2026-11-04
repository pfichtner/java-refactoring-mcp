# Aggressive inline: captured locals renamed consistently


### Input:
```java
public class Foo {
    public int run() {
        int result = 100;
        int picked = pick(5);
        return picked + result;
    }

    private int pick(int value) {
        int result = value + 1;
        Runnable lambda = () -> System.out.println(result + value);
        Runnable anonymous = new Runnable() {
            public void run() {
                System.out.println(result + value);
            }
        };
        class Inner {
            int sum() {
                return result + value;
            }
        }
        lambda.run();
        anonymous.run();
        return new Inner().sum();
    }
}
```

### Refactoring:
**inline method** `pick(5)` at line 4, col 22  
captured parameter and colliding local are renamed inside lambda, anonymous and local class

### Output:
```java
public class Foo {
    public int run() {
        int result = 100;
        int __inline_value = 5;
		int __inline_result1;
		__inline_label: {
			int __inline_result = __inline_value + 1;
			Runnable lambda = () -> System.out.println(__inline_result + __inline_value);
			Runnable anonymous = new Runnable() {
				public void run() {
					System.out.println(__inline_result + __inline_value);
				}
			};
			class Inner {
				int sum() {
					return __inline_result + __inline_value;
				}
			}
			lambda.run();
			anonymous.run();
			{
				__inline_result1 = new Inner().sum();
				break __inline_label;
			}
		}
		int picked = __inline_result1;
        return picked + result;
    }

    private int pick(int value) {
        int result = value + 1;
        Runnable lambda = () -> System.out.println(result + value);
        Runnable anonymous = new Runnable() {
            public void run() {
                System.out.println(result + value);
            }
        };
        class Inner {
            int sum() {
                return result + value;
            }
        }
        lambda.run();
        anonymous.run();
        return new Inner().sum();
    }
}
```