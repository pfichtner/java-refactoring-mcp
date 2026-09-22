# Introduce indirection: Service.process → static doProcess(Service, String)


### Input:
```java
public class Service {

    public String process(String input) {
        return input.trim();
    }
}
```

### Refactoring:
**introduce indirection** `process(String)` → adds `public static String doProcess(Service service, String input)`  
line 3, col 19

### Output:
```java
public class Service {

    public String process(String input) {
        return input.trim();
    }


    public static String doProcess(Service service, String input) {
        return service.process(input);
    }
}
```