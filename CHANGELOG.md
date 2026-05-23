# Changelog

All notable changes to Deeper And Darker — Arcadia Fix Fork are documented here.

---

## [1.4-arcadia-fix.2] - 2026-05-23

### Fixed (English first)

- **Sculk Transmitter long-distance crash** — Wrapped the body of `SculkTransmitterItem.transmit(Level, Player, ItemStack, BlockPos)` in a `try / catch (Throwable)` block via ASM. When the storage block is more than ~15 000 blocks from the player, or in an unloaded dimension, the chunk-load / `gameEvent` / `useWithoutItem` path could throw (NPE from `MinecraftServer.getLevel(dim)` returning `null`, AIOOBE in cross-dimensional listener-registry dispatch, etc.) and crash the server tick. The exception is now caught, logged once via `SafeListHelper.logTransmitterError(Throwable)`, and the use action returns `InteractionResult.FAIL` instead of crashing.
- **Patcher multi-mode** — `asm_patcher/Patcher.java` now takes a mode argument (`amber` | `transmitter`) and routes to the matching transformer. The `amber` mode behaves exactly as before; the `transmitter` mode installs the new try-catch wrapper.

### Correctifs (French mirror)

- **Crash Sculk Transmitter à grande distance** — Le corps de `SculkTransmitterItem.transmit(Level, Player, ItemStack, BlockPos)` est désormais encapsulé dans un `try / catch (Throwable)` via ASM. Quand le bloc de stockage est à plus de ~15 000 blocs du joueur, ou dans une dimension non chargée, le chemin chargement de chunk / `gameEvent` / `useWithoutItem` pouvait lever (NPE car `MinecraftServer.getLevel(dim)` renvoie `null`, AIOOBE dans le dispatch d'évènement inter-dimensions, etc.) et crasher le tick serveur. L'exception est maintenant capturée, loggée une fois via `SafeListHelper.logTransmitterError(Throwable)`, et l'action renvoie `InteractionResult.FAIL` au lieu de crasher.
- **Patcher multi-mode** — `asm_patcher/Patcher.java` prend désormais un argument de mode (`amber` | `transmitter`) et route vers la transformation correspondante. Le mode `amber` se comporte exactement comme avant ; le mode `transmitter` installe le nouvel emballage try-catch.

---

## [1.4-arcadia-fix.1] - 2026-05-05

### Fixed (English first)

- **Crystallized Amber crash on empty loot table** — `CrystallizedAmberBlockEntity.generateFossil()` was calling `List.getFirst()` on the result of `LootTable.getRandomItems()` without checking the list. When other mods empty the `deeperdarker:chests/crystallized_amber` loot table (e.g. via LootJS) or a random roll produces no items, the call threw `NoSuchElementException` and crashed the server tick. ASM patch injects `SafeListHelper.firstOrEmpty(list)` which returns `ItemStack.EMPTY` for empty/null lists.

### Correctifs (French mirror)

- **Crash Crystallized Amber sur loot table vide** — `CrystallizedAmberBlockEntity.generateFossil()` appelait `List.getFirst()` sur le résultat de `LootTable.getRandomItems()` sans vérification. Quand d'autres mods vident la loot table `deeperdarker:chests/crystallized_amber` (ex. via LootJS) ou qu'un tirage aléatoire ne produit aucun item, l'appel levait `NoSuchElementException` et crashait le tick serveur. Le patch ASM injecte `SafeListHelper.firstOrEmpty(list)` qui renvoie `ItemStack.EMPTY` pour les listes vides ou null.

---
