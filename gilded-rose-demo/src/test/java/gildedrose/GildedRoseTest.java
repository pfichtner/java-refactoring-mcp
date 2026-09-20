package gildedrose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GildedRoseTest {

    private void update(GildedRose app, int days) {
        for (int d = 0; d < days; d++) {
            app.updateQuality();
        }
    }

    @Test
    void normalItemDegradesOnePerDayAndAgainAfterExpiry() {
        Item item = new Item("+5 Dexterity Vest", 10, 20);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(9, item.sellIn);
        assertEquals(19, item.quality);

        update(app, 9);
        assertEquals(0, item.sellIn);
        assertEquals(10, item.quality);

        update(app, 1);
        assertEquals(-1, item.sellIn);
        assertEquals(8, item.quality);
    }

    @Test
    void normalItemQualityNeverGoesNegative() {
        Item item = new Item("Elixir of the Mongoose", 2, 1);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 6);
        assertEquals(-4, item.sellIn);
        assertEquals(0, item.quality);
    }

    @Test
    void agedBrieIncreasesWithAgeAndFasterAfterExpiry() {
        Item item = new Item("Aged Brie", 3, 0);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 3);
        assertEquals(0, item.sellIn);
        assertEquals(3, item.quality);

        update(app, 1);
        assertEquals(-1, item.sellIn);
        assertEquals(5, item.quality);
    }

    @Test
    void agedBrieQualityIsCappedAtFifty() {
        Item item = new Item("Aged Brie", -1, 49);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 5);
        assertEquals(50, item.quality);
    }

    @Test
    void sulfurasNeverChanges() {
        Item item = new Item("Sulfuras, Hand of Ragnaros", 0, 80);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 10);
        assertEquals(0, item.sellIn);
        assertEquals(80, item.quality);
    }

    @Test
    void sulfurasIgnoresNegativeSellIn() {
        Item item = new Item("Sulfuras, Hand of Ragnaros", -1, 80);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 10);
        assertEquals(-1, item.sellIn);
        assertEquals(80, item.quality);
    }

    @Test
    void backstagePassesIncreaseByOneUpToTenDaysBefore() {
        Item item = new Item("Backstage passes to a TAFKAL80ETC concert", 11, 20);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(10, item.sellIn);
        assertEquals(21, item.quality);
    }

    @Test
    void backstagePassesGetExtraPlusOneInsideTenDays() {
        Item item = new Item("Backstage passes to a TAFKAL80ETC concert", 10, 20);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(9, item.sellIn);
        assertEquals(22, item.quality);
    }

    @Test
    void backstagePassesGetExtraTwoInsideFiveDays() {
        Item item = new Item("Backstage passes to a TAFKAL80ETC concert", 5, 20);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(4, item.sellIn);
        assertEquals(23, item.quality);
    }

    @Test
    void backstagePassesDropToZeroAfterConcert() {
        Item item = new Item("Backstage passes to a TAFKAL80ETC concert", 0, 20);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(-1, item.sellIn);
        assertEquals(0, item.quality);
    }

    @Test
    void backstagePassesQualityIsCappedAtFifty() {
        Item item = new Item("Backstage passes to a TAFKAL80ETC concert", 6, 49);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(5, item.sellIn);
        assertEquals(50, item.quality);
    }

    @Test
    void conjuredBehavesLikeNormalUntilAConjuredRuleExists() {
        Item item = new Item("Conjured Mana Cake", 3, 6);
        GildedRose app = new GildedRose(new Item[] {item});

        update(app, 1);
        assertEquals(2, item.sellIn);
        assertEquals(5, item.quality);
    }
}