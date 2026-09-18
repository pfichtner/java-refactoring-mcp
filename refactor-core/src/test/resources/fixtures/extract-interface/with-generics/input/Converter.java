public class Converter {
    public <T> T identity(T value) {
        return value;
    }

    public String stringify(Object obj) {
        return obj.toString();
    }
}
