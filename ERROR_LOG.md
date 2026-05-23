# Error Log — Deeper And Darker Arcadia Fix Fork

## [2026-05-23 21:35] — Sculk Transmitter crash at >15 000 blocks from storage block

**Context:** User reported that using a Sculk Transmitter when standing more than ~15 000 blocks from the linked storage block crashes the game on the Arcadia V2 modpack (MC 1.21.1 / NeoForge).

**Error:** No usable crash log was retrievable; from `SculkTransmitterItem.transmit(Level, Player, ItemStack, BlockPos)` bytecode and the upstream Kyanite source, the candidate crash sites at very long distance / cross-dimension are:
1. `MinecraftServer.getLevel(transmitter.linkedPos().get().dimension())` returning `null`, then `linkedLevel.isLoaded(linkedPos)` → NPE.
2. `linkedLevel.gameEvent(GameEvent.ENTITY_INTERACT, player.blockPosition(), ctx)` — cross-dimension dispatch on a position whose section coordinates fall outside the linked level's section bounds → AIOOBE in `LevelChunk.getListenerRegistry(int)`.
3. `linkedLevel.getBlockState(linkedPos).useWithoutItem(...)` — opening a menu on a block in an unloaded chunk that the preceding `addRegionTicket(TicketType.UNKNOWN, chunkPos, 1, chunkPos)` did not finish loading synchronously.

**Root cause:** Mod assumes `linkedLevel` is non-null and that the chunk-load / gameEvent / useWithoutItem path is exception-free regardless of player–storage distance and dimension. No defensive handling at any of the three failure sites.

**Fix:** ASM-installed `try { ... } catch (Throwable t) { SafeListHelper.logTransmitterError(t); return InteractionResult.FAIL; }` around the entire `transmit()` method body. Implementation in `asm_patcher/Patcher.java` mode `transmitter`; helper method in `helper-src/com/kyanite/deeperdarker/SafeListHelper.java`. The exception table covers offsets 0–261, target 261, type `java/lang/Throwable`. Verified via `javap -v` on the repackaged jar.

**Prevention:** When a mod method orchestrates several JVM-level cross-dimension / chunk-load operations in sequence without defensive checks, treat the whole orchestration as a "danger zone" — install a single try-catch at the entry of the method that returns the appropriate failure result, rather than trying to identify and patch each individual call site. This is robust against future MC / NeoForge versions changing which call exactly throws.

---
