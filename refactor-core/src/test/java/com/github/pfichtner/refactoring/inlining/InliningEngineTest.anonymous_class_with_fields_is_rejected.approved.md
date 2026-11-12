# Aggressive inline — rejected: anonymous class has fields


### Input:
```java
class Foo {
    Runnable make() {
        return new Runnable() {
            private int count = 0;

            @Override
            public void run() {
                count++;
            }
        };
    }
}
```

### Refactoring:
**flatten anonymous class** anonymous Runnable  
rejected: anonymous class has field declarations beyond the SAM override

### Diagnostic:
```
anonymous class has 2 body declarations; expected exactly the SAM override
```