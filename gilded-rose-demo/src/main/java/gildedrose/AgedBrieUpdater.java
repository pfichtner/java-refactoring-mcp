package gildedrose;

public final class AgedBrieUpdater implements ItemUpdater {

    @Override
    public boolean canHandle(Item item) {
        return "Aged Brie".equals(item.name);
    }

    @Override
    public void update(Item item) {
        if (item.quality < 50) {
            item.quality = item.quality + 1;
        }
        item.sellIn = item.sellIn - 1;
        if (item.sellIn < 0) {
            if (item.quality < 50) {
                item.quality = item.quality + 1;
            }
        }
    }
}
