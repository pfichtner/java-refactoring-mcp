class GenericBox<T> {
    T item;
    @SuppressWarnings("unchecked")
    final T get() {
        return (T) item;
    }
}

@SuppressWarnings("rawtypes")
public class RawBox extends GenericBox {
    public Object run() {
        return get();
    }
}
