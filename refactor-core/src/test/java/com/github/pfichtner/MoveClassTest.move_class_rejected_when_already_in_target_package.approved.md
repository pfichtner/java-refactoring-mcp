# Move class rejected: already in target package


### Input: Calculator.java:
```java
package com.example.service;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**move class** `com.example.service.Calculator` → `com.example.service` (same package)  
target: com.example.service

### Diagnostic:
```
'Calculator' is already in package 'com.example.service'.
```