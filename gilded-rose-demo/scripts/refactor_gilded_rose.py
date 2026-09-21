#!/usr/bin/env python3
"""Gilded Rose → Chain of Responsibility via the java-refactoring MCP tools.

This deterministically drives the refactoring server so the whole recipe can be
replayed (e.g. under `asciinema rec`) and every milestone's tests stay green.

Recipe (Parallel Change = Expand → Migrate → Contract):

  EXPAND    write the ItemUpdater abstraction + one rule per item type as
            empty shells that only know *which* items they handle.
            GildedRose and its four legacy helpers stay untouched.
  MIGRATE   for each item type in turn:
              move_method   GildedRose.updateXxx          -> XxxUpdater
              rename        updateXxx                     -> update (interface name)
              the moved logic is now owned by the rule; the dispatcher branch
              is rewired to the rule. Tests green at every step.
  CONTRACT  the four legacy helpers are dead code. Replace the name-string
            dispatch with a chain iteration over List<ItemUpdater>, then drop
            the dead helpers and the now-unused name constants. Tests green.

Golden master: TexttestFixtureGoldensTest pins the full output so ANY behavior
change (not just a compile) fails the suite.
"""
import pathlib
import re
import subprocess
import sys

from mcp_client import McpClient

DEMO = pathlib.Path("/workspace/gilded-rose-demo")
SRC = DEMO / "src" / "main" / "java" / "gildedrose"

MOVE_TO = {
    "updateAgedBrie": "AgedBrieUpdater",
    "updateBackstagePass": "BackstagePassUpdater",
    "updateSulfuras": "SulfurasUpdater",
    "updateNormal": "RegularUpdater",
}

PACKAGE = "gildedrose"

GILDEDROSE = SRC / "GildedRose.java"


def sh(cmd, **kw):
    print("$ " + " ".join(cmd))
    proc = subprocess.run(cmd, capture_output=True, text=True, **kw)
    print(proc.stdout, end="")
    if proc.stdout and "Tests run:" in proc.stdout:
        print(proc.stdout.split("Tests run:")[1].splitlines()[0].strip())
    if proc.returncode != 0:
        print(proc.stderr[-2000:])
        sys.exit(1)
    return proc


def banner(title):
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def write(path, content):
    path.write_text(content)
    print(f"wrote {path.relative_to(DEMO)}")


def parse_file_blocks(text):
    """move_method returns '=== Name.java ===\n<source>\n\n(=== other ===\n...')"""
    blocks = {}
    for m in re.finditer(r"=== (.+?) ===\n(.*?)(?=\n=== |\Z)", text, re.S):
        blocks[m.group(1)] = m.group(2).strip("\n")
    return blocks


def call(tool, **args):
    out = client.call(tool, args)
    if out["isError"]:
        print(out["text"])
        sys.exit(1)
    return out["text"]


def expand(sh, path):
    write(path / "ItemUpdater.java", ITEM_UPDATER)
    write(path / "AgedBrieUpdater.java", SHELL("AgedBrieUpdater"))
    write(path / "BackstagePassUpdater.java", SHELL("BackstagePassUpdater"))
    write(path / "SulfurasUpdater.java", SHELL("SulfurasUpdater"))
    write(path / "RegularUpdater.java", SHELL("RegularUpdater"))
    sh(["mvn", "-q", "test"], cwd=DEMO)


def migrate(path):
    for helper, rule in MOVE_TO.items():
        banner(f"MIGRATE: move {helper} → {rule}")
        src = SRC / f"{rule}.java"

        text = call("move_method",
                    project_root=str(DEMO),
                    file=str(GILDEDROSE),
                    method=helper,
                    target_class=f"{PACKAGE}.{rule}")
        blocks = parse_file_blocks(text)
        for name in (f"{rule}.java", "GildedRose.java"):
            assert name in blocks, text
        write(GILDEDROSE, blocks["GildedRose.java"])
        write(src, blocks[f"{rule}.java"])

        text = call("analyze_refactoring",
                    project_root=str(DEMO),
                    file=str(src),
                    method=helper,
                    refactoring="rename",
                    new_name="update")
        blocks = parse_file_blocks(text)
        write(src, blocks[f"{rule}.java"])

        content = src.read_text()
        content = content.replace(
            f"public abstract class {rule} implements ItemUpdater {{",
            f"public final class {rule} implements ItemUpdater {{")
        content = content.replace(
            "    private void update(Item item) {",
            "    @Override\n    public void update(Item item) {")
        write(src, content)

        content = GILDEDROSE.read_text()
        branch = {
            "updateAgedBrie": "AGED_BRIE.equals(item.name)",
            "updateBackstagePass": "BACKSTAGE_PASS.equals(item.name)",
            "updateSulfuras": "SULFURAS.equals(item.name)",
        }.get(helper, None)
        old_call = f"                {helper}(item);"
        new_call = f"                new {rule}().update(item);"
        assert old_call in content, (old_call, content)
        write(GILDEDROSE, content.replace(old_call, new_call))

        sh(["mvn", "-q", "test"], cwd=DEMO)


def contract(path):
    banner("CONTRACT: replace the name-string dispatch with the chain, drop dead code")
    write(GILDEDROSE, FINAL_GILDEDROSE)
    sh(["mvn", "-q", "test"], cwd=DEMO)

    banner("Final state of the codebase")
    for f in sorted((path).glob("*.java")):
        print(f"\n### {f.name}\n")
        print(f.read_text())


ITEM_UPDATER = """\
package gildedrose;

public interface ItemUpdater {

    boolean canHandle(Item item);

    void update(Item item);
}
"""

BASELINE_GILDEDROSE = """\
package gildedrose;

public class GildedRose {

    private static final String AGED_BRIE = "Aged Brie";
    private static final String BACKSTAGE_PASS = "Backstage passes to a TAFKAL80ETC concert";
    private static final String SULFURAS = "Sulfuras, Hand of Ragnaros";

    Item[] items;

    public GildedRose(Item[] items) {
        this.items = items;
    }

    public void updateQuality() {
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (AGED_BRIE.equals(item.name)) {
                updateAgedBrie(item);
            } else if (BACKSTAGE_PASS.equals(item.name)) {
                updateBackstagePass(item);
            } else if (SULFURAS.equals(item.name)) {
                updateSulfuras(item);
            } else {
                updateNormal(item);
            }
        }
    }

    private void updateAgedBrie(Item item) {
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

    private void updateBackstagePass(Item item) {
        if (item.quality < 50) {
            item.quality = item.quality + 1;
            if (item.sellIn < 11) {
                if (item.quality < 50) {
                    item.quality = item.quality + 1;
                }
            }
            if (item.sellIn < 6) {
                if (item.quality < 50) {
                    item.quality = item.quality + 1;
                }
            }
        }
        item.sellIn = item.sellIn - 1;
        if (item.sellIn < 0) {
            item.quality = item.quality - item.quality;
        }
    }

    private void updateSulfuras(Item item) {
    }

    private void updateNormal(Item item) {
        if (item.quality > 0) {
            item.quality = item.quality - 1;
        }
        item.sellIn = item.sellIn - 1;
        if (item.sellIn < 0) {
            if (item.quality > 0) {
                item.quality = item.quality - 1;
            }
        }
    }
}
"""


def SHELL(name):
    matched = {
        "AgedBrieUpdater": "return \"Aged Brie\".equals(item.name);",
        "BackstagePassUpdater":
            "return \"Backstage passes to a TAFKAL80ETC concert\".equals(item.name);",
        "SulfurasUpdater": "return \"Sulfuras, Hand of Ragnaros\".equals(item.name);",
        "RegularUpdater": "return true;",
    }[name]
    return f"""\
package gildedrose;

public abstract class {name} implements ItemUpdater {{

    @Override
    public boolean canHandle(Item item) {{
        {matched}
    }}
}}
"""


FINAL_GILDEDROSE = """\
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
"""


if __name__ == "__main__":
    banner("STEP 0 · restore the pristine kata (idempotent)")
    for stale in SRC.glob("*Updater.java"):
        stale.unlink()
    (SRC / "ItemUpdater.java").unlink(missing_ok=True)
    write(SRC / "GildedRose.java", BASELINE_GILDEDROSE)
    sh(["mvn", "-q", "clean", "test"], cwd=DEMO)

    client = McpClient()

    banner("EXPAND · ItemUpdater abstraction + rule shells (legacy code untouched)")
    expand(sh, SRC)

    migrate(SRC)

    contract(SRC)

    client.close()
    banner("DONE · every milestone green; Gilded Rose now runs as a chain of responsibility")