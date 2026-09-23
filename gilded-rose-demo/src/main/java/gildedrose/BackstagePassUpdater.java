package gildedrose;

public final class BackstagePassUpdater implements ItemUpdater {

    @Override
    public boolean canHandle(Item item) {
        return "Backstage passes to a TAFKAL80ETC concert".equals(item.name);
    }

    @Override
    public void update(Item item) {
        if (item.quality < 50) item.quality++;
        if (item.sellIn < 11 && item.quality < 50) item.quality++;
        if (item.sellIn < 6  && item.quality < 50) item.quality++;
        item.sellIn--;
        if (item.sellIn < 0) item.quality = 0;
    }
}