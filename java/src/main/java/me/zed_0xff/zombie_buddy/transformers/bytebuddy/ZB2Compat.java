package me.zed_0xff.zombie_buddy.transformers.bytebuddy;

import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.Remapper;

/**
 * convert
 * from me.zed_0xff.zombie_buddy.Patch
 * to   me.zed_0xff.zombie_buddy.annotations.Patch
 */
public class ZB2Compat extends AbstractTransformer {
    private static final String OLD = "me/zed_0xff/zombie_buddy/Patch";
    private static final String NEW = "me/zed_0xff/zombie_buddy/annotations/Patch";

    @Override
    protected ClassVisitor createVisitor(ClassWriter cw, byte[] classBytes) {
        return new ClassRemapper(cw, new Remapper(ASM_API) {
            @Override
            public String map(String internalName) {
                if (internalName.equals(OLD) || internalName.startsWith(OLD + "$")) {
                    setModified();
                    String newName = NEW + internalName.substring(OLD.length());
                    // Logger.debug(internalName, newName);
                    return newName;
                }

                return internalName;
            }
        });
    }
}
