import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;

/**
 * Multi-target ASM patcher for the Deeper-and-Darker Arcadia fix fork.
 *
 * Args: <mode> <input.class> <output.class>
 *
 * Modes:
 *   amber       — patches CrystallizedAmberBlockEntity.generateFossil()
 *                 List.getFirst() -> SafeListHelper.firstOrEmpty(List).
 *   transmitter — wraps SculkTransmitterItem.transmit() in a try/catch
 *                 (Throwable) that logs via SafeListHelper.logTransmitterError
 *                 and returns InteractionResult.FAIL. Prevents crashes when
 *                 the linked storage block is far (~15k+ blocks) or in an
 *                 unloaded dimension.
 */
public class Patcher {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java Patcher <amber|transmitter> <input.class> <output.class>");
            System.exit(2);
        }
        String mode = args[0];
        String input = args[1];
        String output = args[2];

        byte[] bytes = Files.readAllBytes(Paths.get(input));
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor;
        switch (mode) {
            case "amber":
                visitor = amberVisitor(writer);
                break;
            case "transmitter":
                visitor = transmitterVisitor(writer);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }

        reader.accept(visitor, ClassReader.SKIP_FRAMES);
        Files.write(Paths.get(output), writer.toByteArray());
        System.out.println("Done patching (" + mode + ") " + input);
    }

    /**
     * Replace List.getFirst() with SafeListHelper.firstOrEmpty() inside
     * CrystallizedAmberBlockEntity.generateFossil().
     */
    private static ClassVisitor amberVisitor(ClassWriter writer) {
        return new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"generateFossil".equals(name)) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                        if ("java/util/List".equals(owner) && "getFirst".equals(mName)) {
                            System.out.println("Patching: List.getFirst() -> SafeListHelper.firstOrEmpty()");
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "com/kyanite/deeperdarker/SafeListHelper",
                                "firstOrEmpty",
                                "(Ljava/util/List;)Lnet/minecraft/world/item/ItemStack;",
                                false
                            );
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.CHECKCAST && "net/minecraft/world/item/ItemStack".equals(type)) {
                            System.out.println("Removing redundant CHECKCAST ItemStack");
                            return;
                        }
                        super.visitTypeInsn(opcode, type);
                    }
                };
            }
        };
    }

    /**
     * Wrap the body of SculkTransmitterItem.transmit(Level, Player, ItemStack,
     * BlockPos) in a try/catch (Throwable) handler. On any uncaught throwable
     * (NPE from null linkedLevel, AIOOBE inside ServerLevel.gameEvent or chunk
     * listener registry, chunk loading failures at huge distances), the handler:
     *
     *   1. Pops the throwable from the stack into a fresh local.
     *   2. Calls SafeListHelper.logTransmitterError(Throwable).
     *   3. Returns InteractionResult.FAIL.
     *
     * Net effect: the use action fails cleanly and the game does not crash.
     */
    private static ClassVisitor transmitterVisitor(ClassWriter writer) {
        return new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"transmit".equals(name)) return mv;
                if (!"(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;".equals(descriptor)) {
                    return mv;
                }
                System.out.println("Patching: wrapping SculkTransmitterItem.transmit() in try/catch (Throwable)");

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    final Label tryStart = new Label();
                    final Label tryEnd = new Label();
                    final Label handler = new Label();
                    boolean started = false;

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/Throwable");
                        super.visitLabel(tryStart);
                        started = true;
                    }

                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        if (started) {
                            super.visitLabel(tryEnd);
                            super.visitLabel(handler);
                            // [stack: Throwable] -> call logger, then load FAIL and return.
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "com/kyanite/deeperdarker/SafeListHelper",
                                "logTransmitterError",
                                "(Ljava/lang/Throwable;)V",
                                false
                            );
                            super.visitFieldInsn(
                                Opcodes.GETSTATIC,
                                "net/minecraft/world/InteractionResult",
                                "FAIL",
                                "Lnet/minecraft/world/InteractionResult;"
                            );
                            super.visitInsn(Opcodes.ARETURN);
                        }
                        // COMPUTE_MAXS recomputes; pass current hints.
                        super.visitMaxs(maxStack, maxLocals);
                    }
                };
            }
        };
    }
}
