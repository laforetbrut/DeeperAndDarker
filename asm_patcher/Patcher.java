import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;

/**
 * Patches CrystallizedAmberBlockEntity.generateFossil() to skip the
 * loot.getFirst() call when the loot list is empty (NoSuchElementException
 * when other mods empty the deeperdarker:chests/crystallized_amber loot table
 * or random rolls hit nothing).
 *
 * Original:
 *   List<ItemStack> list = table.getRandomItems(lootParams);
 *   this.loot = list.getFirst();    // <-- crash if empty
 *
 * Patched:
 *   List<ItemStack> list = table.getRandomItems(lootParams);
 *   if (!list.isEmpty()) {
 *       this.loot = list.getFirst();
 *   }
 *
 * Approach: replace the call site `List.getFirst()` with a static helper
 * that returns ItemStack.EMPTY when list is empty.
 */
public class Patcher {
    public static void main(String[] args) throws Exception {
        String input = args[0];
        String output = args[1];

        byte[] bytes = Files.readAllBytes(Paths.get(input));
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"generateFossil".equals(name)) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                        // Replace List.getFirst() with our safe helper that returns ItemStack.EMPTY on empty list
                        if ("java/util/List".equals(owner) && "getFirst".equals(mName)) {
                            System.out.println("Patching: List.getFirst() -> SafeListHelper.firstOrEmpty()");
                            // Pop the List (already on stack) and call our helper
                            // Stack before: ..., List
                            // Stack after:  ..., ItemStack
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
                        // The original bytecode also has a checkcast ItemStack after getFirst()
                        // Since our helper returns ItemStack directly, we can drop the checkcast
                        if (opcode == Opcodes.CHECKCAST && "net/minecraft/world/item/ItemStack".equals(type)) {
                            System.out.println("Removing redundant CHECKCAST ItemStack");
                            return; // skip
                        }
                        super.visitTypeInsn(opcode, type);
                    }
                };
            }
        }, 0);

        Files.write(Paths.get(output), writer.toByteArray());
        System.out.println("Done patching " + input);
    }
}
