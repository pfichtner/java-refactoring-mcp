package gildedrose;

import java.util.List;

public class GildedRose {

    private final List<ItemUpdater> chain = List.of(
            new AgedBrieUpdater(),
            new BackstagePassUpdater(),
            new SulfurasUpdater(),
            new RegularUpdater());

    Item[] items;

    public GildedRose(Item[] items) {
        this.items = items;
    }

    public void updateQuality() {
        for (int i = 0; i < items.length; i++) {
            apply(items[i]);
        }
    }

    private void apply(Item item) {
        for (ItemUpdater updater : chain) {
            if (updater.canHandle(item)) {
                updater.update(item);
                return;
            }
        }
        throw new IllegalStateException("No updater for item: " + item.name);
    }
}
