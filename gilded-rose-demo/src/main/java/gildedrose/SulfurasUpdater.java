package gildedrose;

public final class SulfurasUpdater implements ItemUpdater {

    @Override
    public boolean canHandle(Item item) {
        return "Sulfuras, Hand of Ragnaros".equals(item.name);
    }

    @Override
    public void update(Item item) {
    }
}
