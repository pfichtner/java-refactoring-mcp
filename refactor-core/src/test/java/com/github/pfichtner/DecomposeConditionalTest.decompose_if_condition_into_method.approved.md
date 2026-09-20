# Decompose conditional: if (age >= 18 && premium) → isAdultPremium()


### Input:
```java
public class Foo {
    private int age;
    private boolean premium;

    public String classify() {
        if (age >= 18 && premium) {
            return "adult premium";
        }
        return "other";
    }
}
```

### Refactoring:
**decompose conditional** `if (age >= 18 && premium)` → `if (isAdultPremium())`  
condition extracted into private boolean method

### Output:
```java
public class Foo {
    private int age;
    private boolean premium;

    public String classify() {
        if (isAdultPremium()) {
            return "adult premium";
        }
        return "other";
    }


    private boolean isAdultPremium() {
        return age >= 18 && premium;
    }
}
```