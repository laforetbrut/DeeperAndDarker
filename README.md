# Deeper And Darker — Arcadia Fix Fork

## What

A patched build of [Deeper and Darker 1.4](https://www.curseforge.com/minecraft/mc-mods/deeperdarker) for use in the **Arcadia V2 'Echoes Of Power'** modpack (MC 1.21.1 / NeoForge).

## Issue fixed

`CrystallizedAmberBlockEntity.generateFossil()` calls `List.getFirst()` on the result of `LootTable.getRandomItems()` without checking if the list is empty. When the `deeperdarker:chests/crystallized_amber` loot table is modified by other mods (e.g. LootJS rules removing all entries) or when a random roll produces no items, the call throws `NoSuchElementException` and crashes the server tick:

```
java.util.NoSuchElementException: null
  at java.util.List.getFirst(List.java:825)
  at CrystallizedAmberBlockEntity.generateFossil(CrystallizedAmberBlockEntity.java:46)
  at CrystallizedAmberBlock.tick(CrystallizedAmberBlock.java:73)
```

## Fix

ASM bytecode patch:

1. Inject `com.kyanite.deeperdarker.SafeListHelper` into the jar — a tiny helper that returns `ItemStack.EMPTY` for empty/null lists
2. Replace `List.getFirst()` invocation in `CrystallizedAmberBlockEntity.generateFossil()` with `SafeListHelper.firstOrEmpty(list)`
3. Remove the now-redundant `CHECKCAST ItemStack` (helper returns ItemStack directly)

Equivalent at the source level:

```diff
  List<ItemStack> list = table.getRandomItems(lootParams);
- this.loot = list.getFirst();
+ this.loot = SafeListHelper.firstOrEmpty(list);
```

If the loot table is empty, the block stays in its "no loot generated" state — no crash, no visible glitch (the player just won't find any item inside this particular crystallized amber).

## Files

- `original.jar` — upstream `deeperdarker-neoforge-1.21.1-1.4.jar` (unchanged)
- `patched.jar` — fixed jar shipped in the modpack as `deeperdarker-neoforge-1.21.1-1.4-arcadia-fix.jar`
- `extracted/` — extracted contents used to repackage
- `asm_patcher/Patcher.java` — the ASM rewriter source
- `helper-src/com/kyanite/deeperdarker/SafeListHelper.java` — the injected helper class

## Install

Replace `deeperdarker-neoforge-1.21.1-1.4.jar` in your `mods/` folder with `patched.jar` (renamed to `deeperdarker-neoforge-1.21.1-1.4-arcadia-fix.jar`).

## Credits

Original mod by Kyanite Group.
Patch by vyrriox for Arcadia V2.
