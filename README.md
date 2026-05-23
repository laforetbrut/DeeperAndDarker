# Deeper And Darker — Arcadia Fix Fork

A patched build of [Deeper and Darker 1.4](https://www.curseforge.com/minecraft/mc-mods/deeperdarker) for use in the **Arcadia V2 'Echoes Of Power'** modpack (MC 1.21.1 / NeoForge).

## Features

- **Fix #1 — Crystallized Amber crash on empty loot table**
- **Fix #2 — Sculk Transmitter crash at long distance / on unloaded chunk**

## Issues fixed

### 1. `CrystallizedAmberBlockEntity.generateFossil()` — empty loot list

`CrystallizedAmberBlockEntity.generateFossil()` calls `List.getFirst()` on the result of `LootTable.getRandomItems()` without checking if the list is empty. When the `deeperdarker:chests/crystallized_amber` loot table is modified by other mods (e.g. LootJS rules removing all entries) or when a random roll produces no items, the call throws `NoSuchElementException` and crashes the server tick:

```
java.util.NoSuchElementException: null
  at java.util.List.getFirst(List.java:825)
  at CrystallizedAmberBlockEntity.generateFossil(CrystallizedAmberBlockEntity.java:46)
  at CrystallizedAmberBlock.tick(CrystallizedAmberBlock.java:73)
```

### 2. `SculkTransmitterItem.transmit()` — long-distance / unloaded-chunk crash

`SculkTransmitterItem.transmit()` calls `linkedLevel.gameEvent(...)` then `linkedLevel.getBlockState(linkedPos).useWithoutItem(...)` on the linked storage block. When the player is **more than ~15 000 blocks** from the storage block (or the linked dimension is not loaded server-side), any one of these can throw — most commonly an NPE from `MinecraftServer.getLevel(dim)` returning `null`, or an `ArrayIndexOutOfBoundsException` from a section/listener-registry lookup in the cross-dimensional `gameEvent` dispatch. The exception propagates up through the player interaction handler and crashes the server tick.

## Fix

ASM bytecode patches (applied at build time, no runtime hooks, no Mixin):

1. **Inject** `com.kyanite.deeperdarker.SafeListHelper` into the jar — a tiny helper that returns `ItemStack.EMPTY` for empty/null lists and logs caught transmitter exceptions.
2. **`CrystallizedAmberBlockEntity.generateFossil()`** — replace the `List.getFirst()` call with `SafeListHelper.firstOrEmpty(list)`, and drop the now-redundant `CHECKCAST ItemStack`. Equivalent at the source level:
   ```diff
     List<ItemStack> list = table.getRandomItems(lootParams);
   - this.loot = list.getFirst();
   + this.loot = SafeListHelper.firstOrEmpty(list);
   ```
3. **`SculkTransmitterItem.transmit(Level, Player, ItemStack, BlockPos)`** — wrap the entire method body in `try { ... } catch (Throwable t) { SafeListHelper.logTransmitterError(t); return InteractionResult.FAIL; }`. Any unhandled crash in the chunk-load / `gameEvent` / `useWithoutItem` path is logged and the use action fails cleanly. Equivalent at the source level:
   ```diff
     public static InteractionResult transmit(Level level, Player player, ItemStack stack, BlockPos clickedPos) {
   +   try {
         // ... original body ...
   +   } catch (Throwable t) {
   +     SafeListHelper.logTransmitterError(t);
   +     return InteractionResult.FAIL;
   +   }
     }
   ```

When the loot table is empty, the block stays in its "no loot generated" state — no crash, no visible glitch. When the transmitter fails, the player simply gets an `InteractionResult.FAIL` (no menu opens) and the stack trace is logged once to the server log.

## Files

- `original.jar` — upstream `deeperdarker-neoforge-1.21.1-1.4.jar` (unchanged)
- `patched.jar` — fixed jar shipped in the modpack as `deeperdarker-neoforge-1.21.1-1.4-arcadia-fix.jar`
- `extracted/` — extracted contents used to repackage
- `asm_patcher/Patcher.java` — the ASM rewriter source (modes: `amber`, `transmitter`)
- `helper-src/com/kyanite/deeperdarker/SafeListHelper.java` — the injected helper class

## Build

The build is manual; no Gradle script. From the project root, with `ASM_JAR` pointing at any modern `asm-*.jar`:

```sh
# 1. Compile the injected helper against the stubs
javac -d helper-build -cp helper-stubs -sourcepath "helper-src;helper-stubs" \
  helper-src/com/kyanite/deeperdarker/SafeListHelper.java
cp helper-build/com/kyanite/deeperdarker/SafeListHelper.class \
   extracted/com/kyanite/deeperdarker/SafeListHelper.class

# 2. Compile the patcher
javac -d asm_patcher -cp "$ASM_JAR" asm_patcher/Patcher.java

# 3. Apply each patch
java -cp "asm_patcher;$ASM_JAR" Patcher amber \
  asm_patcher/CrystallizedAmberBlockEntity.original.class \
  extracted/com/kyanite/deeperdarker/content/blocks/entity/CrystallizedAmberBlockEntity.class

java -cp "asm_patcher;$ASM_JAR" Patcher transmitter \
  asm_patcher/SculkTransmitterItem.original.class \
  extracted/com/kyanite/deeperdarker/content/items/SculkTransmitterItem.class

# 4. Repackage
jar --create --no-manifest --file=patched.jar -C extracted .
```

## Install

Replace `deeperdarker-neoforge-1.21.1-1.4.jar` in your `mods/` folder with `patched.jar` (renamed to `deeperdarker-neoforge-1.21.1-1.4-arcadia-fix.jar`).

## Credits

Original mod by Kyanite Group.
Patch by vyrriox for Arcadia V2.

---

# Deeper And Darker — Arcadia Fix Fork (Version Française)

Build patché de [Deeper and Darker 1.4](https://www.curseforge.com/minecraft/mc-mods/deeperdarker) pour le modpack **Arcadia V2 « Echoes Of Power »** (MC 1.21.1 / NeoForge).

## Caractéristiques

- **Correctif #1 — crash Crystallized Amber sur loot table vide**
- **Correctif #2 — crash Sculk Transmitter à grande distance / sur chunk déchargé**

## Problèmes corrigés

### 1. `CrystallizedAmberBlockEntity.generateFossil()` — liste de loot vide

`generateFossil()` appelle `List.getFirst()` sur le résultat de `LootTable.getRandomItems()` sans vérifier que la liste n'est pas vide. Quand d'autres mods modifient la loot table `deeperdarker:chests/crystallized_amber` (ex. règles LootJS qui suppriment toutes les entrées) ou quand le tirage aléatoire ne produit aucun item, l'appel lève `NoSuchElementException` et crashe le tick serveur.

### 2. `SculkTransmitterItem.transmit()` — crash à grande distance / chunk non chargé

`transmit()` appelle `linkedLevel.gameEvent(...)` puis `linkedLevel.getBlockState(linkedPos).useWithoutItem(...)` sur le bloc de stockage lié. Quand le joueur est **à plus de ~15 000 blocs** du bloc de stockage (ou que la dimension liée n'est pas chargée côté serveur), l'un de ces appels peut lever — le plus souvent un NPE parce que `MinecraftServer.getLevel(dim)` renvoie `null`, ou un `ArrayIndexOutOfBoundsException` dans la dispatch d'évènement inter-dimensions. L'exception remonte au handler d'interaction et crashe le tick serveur.

## Correctif

Patches bytecode ASM (appliqués au build, aucun hook runtime, aucun Mixin) :

1. **Injection** de `com.kyanite.deeperdarker.SafeListHelper` dans le jar.
2. **`generateFossil()`** — remplacement de `List.getFirst()` par `SafeListHelper.firstOrEmpty(list)`.
3. **`transmit()`** — emballage du corps entier de la méthode dans un `try / catch (Throwable)` qui logge via `SafeListHelper.logTransmitterError(t)` et renvoie `InteractionResult.FAIL`.

Quand la loot table est vide, le bloc reste dans son état "pas de loot généré" — pas de crash. Quand le transmitter échoue, le joueur reçoit simplement `InteractionResult.FAIL` (aucun menu ne s'ouvre) et la stack trace est loggée une fois dans le log serveur.

## Installation

Remplacer `deeperdarker-neoforge-1.21.1-1.4.jar` dans `mods/` par `patched.jar` (renommé en `deeperdarker-neoforge-1.21.1-1.4-arcadia-fix.jar`).

## Credits

Mod original : Kyanite Group.
Patch : vyrriox pour Arcadia V2.
