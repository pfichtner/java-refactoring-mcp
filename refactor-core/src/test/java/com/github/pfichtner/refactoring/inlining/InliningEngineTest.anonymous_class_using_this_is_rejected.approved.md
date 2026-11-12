# Aggressive inline — rejected: anonymous class uses 'this'


### Input:
```java
class Foo {
    Runnable make() {
        return new Runnable() {
            @Override
            public void run() {
                System.out.println(this.getClass().getSimpleName());
            }
        };
    }
}
```

### Refactoring:
**flatten anonymous class** anonymous Runnable  
rejected: 'this' in lambda would refer to the enclosing type, not the anonymous class

### Diagnostic:
```
SAM method body uses 'this'; in a lambda 'this' would refer to the enclosing type, not the anonymous class instance
```