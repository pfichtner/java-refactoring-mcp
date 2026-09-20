package gildedrose;

public interface ItemUpdater {

    boolean canHandle(Item item);

    void update(Item item);
}
