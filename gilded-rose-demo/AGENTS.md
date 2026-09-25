# Gilded Rose → Chain of Responsibility (demo session directives)

You are running a showcase of the **java-refactoring MCP** server. The audience
is watching a terminal recording: every move should be deliberate, narrated in
one clear sentence before you do it, and every milestone must end with a green
test run.

You will transform this kata with **Parallel Change** (Expand → Migrate →
Contract). Never touch a `.java` file as raw text when the MCP tool can do the
job; use `move_method` and `rename` for all code movement. Check the kitchen
sink moved into the strategy pattern first.

## Tools

The MCP server exposes one tool per refactoring (`move_method`, `rename`,
`extract_method`, `remove_method`) plus `list_refactorings` to enumerate them.
All refactoring tools:

- take `project_root` = `/workspace/gilded-rose-demo`,
- take `file` = absolute path of the touched source file,
- run as a **dry-run by default**: they return `=== FileName.java ===\n<new source>`
  blocks and write nothing to disk,
- write the changes themselves when you pass **`apply: true`**, and answer with a
  short summary instead of the full source.

The `rename` tool is first-class (no more `refactoring: "rename"`): it takes a
`method`/`field`/`type` locator plus `new_name`, and updates every reference
across the project.

## Milestone 0 — ground truth

- Run `mvn test`. All 13 tests must pass, including the golden-master
  `TexttestFixtureGoldensTest` that pins the full 10-day output.
- Show the audience the classic smell: `GildedRose.updateQuality()` is an
  if/else ladder over the four name constants, with one private
  `updateX(Item)` helper per item type.

## Milestone 1 — EXPAND (green)

Create the abstraction *alongside* the legacy code. Do not touch
`GildedRose.java` yet.

1. Create `src/main/java/gildedrose/ItemUpdater.java`:

   ```java
   package gildedrose;

   public interface ItemUpdater {

       boolean canHandle(Item item);

       void update(Item item);
   }
   ```

2. Create four rule classes. Each is an **abstract** shell (it compiles even
   though `update` is not implemented yet) whose only behaviour is to know
   which items it handles:

   - `AgedBrieUpdater` → `canHandle` matches `"Aged Brie"`
   - `BackstagePassUpdater` → `canHandle` matches `"Backstage passes to a TAFKAL80ETC concert"`
   - `SulfurasUpdater` → `canHandle` matches `"Sulfuras, Hand of Ragnaros"`
   - `RegularUpdater` → `canHandle` is `return true;` (the fallback, always
     last in the chain)

   ```java
   package gildedrose;

   public abstract class AgedBrieUpdater implements ItemUpdater {

       @Override
       public boolean canHandle(Item item) {
           return "Aged Brie".equals(item.name);
       }
   }
   ```

3. `mvn test` → still green. The legacy code is untouched; the abstraction
   exists in parallel. That is the "expand".

## Milestone 2 — MIGRATE (one item type at a time, green every step)

For each type in this order — `AgedBrie` → `BackstagePass` → `Sulfuras` →
`Regular` — do the same four moves. The helper lives in `GildedRose.java`, the
rule class name is the helper's name with the target class from the list.

For each pair:

1. **`move_method`** — tell the audience you are physically moving the
   behaviour into its own class. Show them the dry-run output first, then
   re-run with `apply=true`:
   ```
   move_method
     project_root=/workspace/gilded-rose-demo
     file=/workspace/gilded-rose-demo/src/main/java/gildedrose/GildedRose.java
     method=<helper, e.g. updateAgedBrie>
     target_class=<rule, e.g. AgedBrieUpdater>
     apply=true
   ```
   The server writes the new `GildedRose.java` and `<Rule>.java` to disk itself
   and returns a summary.

2. **`rename`** the just-moved method to the interface name. Preview, narrate,
   then apply:
   ```
   rename
     project_root=/workspace/gilded-rose-demo
     file=/workspace/gilded-rose-demo/src/main/java/gildedrose/<Rule>.java
     method=<helper, e.g. updateAgedBrie>
     new_name=update
     apply=true
   ```
   (The move landed a package-private or private `update…(Item)` method.
   Finishing the interface contract is a two-token edit the tool has no
   operation for: make the class `final` and the method `public` with
   `@Override`.)

3. **Add a delegation stub** back to `GildedRose.java` so the old if/else
   dispatch keeps compiling and the tests stay green:
   ```java
   private void updateAgedBrie(Item item) {
       new AgedBrieUpdater().update(item);
   }
   ```
   (direct write — the tool moved the body out, so this thin bridge is the only
   new code you write by hand.)

4. `mvn test` → green. Show the audience the new rule file briefly.

When all four types are migrated, the dispatcher still uses the name-based
if/else; each branch now delegates through one of the four stubs, which in turn
calls the rule class. The stubs are scaffolding — they exist only to keep the
old dispatch wiring intact until CONTRACT replaces it.

## Milestone 3 — CONTRACT (green)

### Step 1 — introduce the chain (stubs become dead code)

Write the final `GildedRose.java` with the chain field, `updateQuality` loop,
and `apply` method — **but keep the four delegation stubs in the file for now**.
They are dead (nothing calls them anymore) but the class still compiles and all
13 tests pass:

```java
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

    // dead stubs — will be removed in step 2
    private void updateAgedBrie(Item item)      { new AgedBrieUpdater().update(item); }
    private void updateBackstagePass(Item item) { new BackstagePassUpdater().update(item); }
    private void updateSulfuras(Item item)      { new SulfurasUpdater().update(item); }
    private void updateNormal(Item item)        { new RegularUpdater().update(item); }
}
```

`mvn test` → 13 green.

### Step 2 — remove the dead stubs with `remove_method`

For each stub in turn, call `remove_method` (cascade=false — these are private,
no subclasses involved) with `apply=true` so the server rewrites the file:

```
remove_method
  project_root=/workspace/gilded-rose-demo
  file=/workspace/gilded-rose-demo/src/main/java/gildedrose/GildedRose.java
  method=updateAgedBrie
  cascade=false
  apply=true
```

Repeat for `updateBackstagePass`, `updateSulfuras`, `updateNormal`.

`mvn test` → all 13 green. `GildedRose.java` must end up as:

```java
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
```

## Epilogue — prove equivalence

- `mvn test` → all 13 green, golden master byte-identical, so behaviour is
  unchanged after the entire migration.
- Optionally run the fixture: `mvn -q exec:java -Dexec.mainClass=gildedrose.TexttestFixture -Dexec.args=9`
  and point at how clean `GildedRose` is compared with the start.
- Accomplish the last mile: trace one item (e.g. an Aged Brie) through the
  chain: `AgedBrieUpdater.canHandle` answers true → its `update` runs → done;
  a normal item falls through to the `RegularUpdater` tail.

## Rules of the session

- One refactoring per pull; narrate before you act.
- Never use `sed`, `awk`, or string-replace to move Java code — only
  `move_method` / `rename`.
- New files, the `private → public/@Override/final` contract finishing, and the
  final dead-code cleanup are the only direct writes; say so when you make one.
- After every milestone run `mvn test` and report the count (`Tests run: 13`).
- If a tool returns an error, read it, fix the arguments, retry — do not
  improvise around the tool.