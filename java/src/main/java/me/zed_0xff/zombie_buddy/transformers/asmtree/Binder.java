package me.zed_0xff.zombie_buddy.transformers.asmtree;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

/** {@link Patch.Adapter.Method} stubs → static {@link java.lang.invoke.MethodHandle} + {@code invokeExact} body; handles wired by explicit {@code init(): boolean}. */
public class Binder extends AbstractTransformer {

    private static final String REFLECT       = "me/zed_0xff/zombie_buddy/Reflect";
    private static final String MH            = "java/lang/invoke/MethodHandle";
    private static final int INIT_OK_SLOT     = 0;

    @Override
    protected boolean transformNode(ClassNode cn) {
        TypeDescription adapterTd = m_ctx.getCurrentTypeDesc();
        var adapterAnn = adapterTd.getDeclaredAnnotations().ofType(Patch.Adapter.class);
        if (adapterAnn == null) return false;

        String targetBin = adapterAnn.load().value();
        TypeDescription td = m_ctx.jarContext().getOrigTypeDesc(targetBin);
        if (td == null) {
            Logger.once.warn("Binder: unresolved adapter target", targetBin);
            return false;
        }

        String targetIn = td.getInternalName();
        String delegate = delegateField(cn, targetIn);
        if (delegate == null) {
            Logger.once.warn("Binder: no delegate field for target type on", cn.name);
            return false;
        }

        ArrayList<Bind> binds = new ArrayList<>();
        for (int i = 0, seq = 0; i < cn.methods.size(); i++) {
            MethodNode mn = cn.methods.get(i);
            if ("<clinit>".equals(mn.name) || "<init>".equals(mn.name)) continue;
            if ((mn.access & ACC_STATIC) != 0) continue;

            MethodDescription.InDefinedShape adapterMethod = method(adapterTd, mn);
            if (adapterMethod == null) continue;
            if (adapterMethod.getDeclaredAnnotations().isAnnotationPresent(Patch.Adapter.Intrinsic.class)) continue;

            var bindAnn = adapterMethod.getDeclaredAnnotations().ofType(Patch.Adapter.Method.class);
            if (bindAnn == null) continue;

            String[] names = resolveMethod(td, mn, bindAnn.load());
            if (names == null) {
                Logger.once.warn("Binder: no unique target method", cn.name, mn.name, mn.desc);
                continue;
            }

            binds.add(new Bind(mn, "zb$mh$" + seq++ + "$" + mn.name, names));
        }

        if (binds.isEmpty()) return false;

        for (Bind b : binds) {
            addFieldIfAbsent(cn, b.field());
            rewriteBody(cn.name, delegate, targetIn, b.mn(), b.field());
        }

        addInit(cn, targetBin, binds);
        return true;
    }

    private record Bind(MethodNode mn, String field, String[] names) {}

    private static MethodDescription.InDefinedShape method(TypeDescription td, MethodNode mn) {
        var ms = td.getDeclaredMethods().filter(m -> m.getInternalName().equals(mn.name) && m.getDescriptor().equals(mn.desc));

        return ms.size() == 1 ? ms.getOnly() : null;
    }

    /** Prefer {@code _instance}, else first instance field typed as the adapter target. */
    private static String delegateField(ClassNode cn, String targetIn) {
        String want = "L" + targetIn + ";";
        FieldNode fb = null, pref = null;

        for (FieldNode f : cn.fields) {
            if ((f.access & ACC_STATIC) != 0) continue;
            if (!want.equals(f.desc)) continue;

            if ("_instance".equals(f.name)) pref = f;
            else if (fb == null) fb = f;
        }

        return pref != null ? pref.name : fb != null ? fb.name : null;
    }

    private static String[] resolveMethod(TypeDescription td, MethodNode mn, Patch.Adapter.Method methAnn) {
        String[] cands = methAnn.value().length == 0 ? new String[] { mn.name } : methAnn.value();
        for (String c : cands) {
            var ms = td.getDeclaredMethods().filter(named(c).and(m -> m.getDescriptor().equals(mn.desc)));
            if (ms.size() == 1) return new String[] { ms.getOnly().getName() };
        }

        return null;
    }

    private static void addFieldIfAbsent(ClassNode cn, String name) {
        for (FieldNode f : cn.fields) {
            if (name.equals(f.name)) return;
        }

        cn.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, name, "Ljava/lang/invoke/MethodHandle;", null, null));
    }

    private static void rewriteBody(String adapterIn, String delegate, String targetIn, MethodNode mn, String mhField) {
        InsnList c = new InsnList();
        c.add(new FieldInsnNode(GETSTATIC, adapterIn, mhField, "Ljava/lang/invoke/MethodHandle;"));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new FieldInsnNode(GETFIELD, adapterIn, delegate, "L" + targetIn + ";"));

        int slot = 1;
        for (Type t : Type.getArgumentTypes(mn.desc)) {
            c.add(load(t, slot));
            slot += t.getSize();
        }

        c.add(new MethodInsnNode(INVOKEVIRTUAL, MH, "invokeExact", ixDesc(targetIn, mn.desc), false));
        c.add(ret(Type.getReturnType(mn.desc)));

        mn.instructions.clear();
        mn.tryCatchBlocks = null;
        mn.localVariables = null;
        mn.visibleLocalVariableAnnotations = null;
        mn.invisibleLocalVariableAnnotations = null;
        mn.instructions = c;
        mn.maxStack = Math.max(8, 3 + Type.getArgumentsAndReturnSizes(mn.desc));
        mn.maxLocals = slot;
    }

    private static String ixDesc(String targetIn, String stubDesc) {
        Type[] oldArgs = Type.getArgumentTypes(stubDesc);
        Type[] newArgs = new Type[oldArgs.length + 1];

        newArgs[0] = Type.getObjectType(targetIn);
        System.arraycopy(oldArgs, 0, newArgs, 1, oldArgs.length);

        return Type.getMethodDescriptor(Type.getReturnType(stubDesc), newArgs);
    }

    private static VarInsnNode load(Type t, int slot) {
        return new VarInsnNode(t.getOpcode(ILOAD), slot);
    }

    private static InsnNode ret(Type t) {
        return new InsnNode(t.getOpcode(IRETURN));
    }

    private static void addInit(ClassNode cn, String targetBin, List<Bind> binds) {
        MethodNode init = null;
        for (MethodNode m : cn.methods) {
            if ("init".equals(m.name) && (m.access & ACC_STATIC) != 0) {
                init = m;
                break;
            }
        }

        if (init == null)
            cn.methods.add(init = new MethodNode(ACC_PUBLIC | ACC_STATIC, "init", "()Z", null, null));

        init.desc = "()Z";

        InsnList p = new InsnList();
        p.add(new InsnNode(ICONST_1));
        p.add(new VarInsnNode(ISTORE, INIT_OK_SLOT));

        for (Bind b : binds) {
            LabelNode found = new LabelNode();

            p.add(new LdcInsnNode(targetBin));
            p.add(new MethodInsnNode(INVOKESTATIC, REFLECT, "on", "(Ljava/lang/Object;)L" + REFLECT + ";", false));
            p.add(classLiteral(Type.getReturnType(b.mn().desc)));
            addClassArray(p, Type.getArgumentTypes(b.mn().desc));
            addStringArray(p, b.names());
            p.add(new MethodInsnNode(INVOKEVIRTUAL, REFLECT, "getMethodHandle",
                    "(Ljava/lang/Class;[Ljava/lang/Class;[Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;", false));
            p.add(new InsnNode(DUP));
            p.add(new FieldInsnNode(PUTSTATIC, cn.name, b.field(), "Ljava/lang/invoke/MethodHandle;"));
            p.add(new JumpInsnNode(IFNONNULL, found));
            p.add(new InsnNode(ICONST_0));
            p.add(new VarInsnNode(ISTORE, INIT_OK_SLOT));
            p.add(found);
        }

        p.add(new VarInsnNode(ILOAD, INIT_OK_SLOT));
        p.add(new InsnNode(IRETURN));

        init.instructions.clear();
        init.tryCatchBlocks = null;
        init.localVariables = null;
        init.instructions = p;
        init.maxLocals = 1;
        init.maxStack = Math.max(8, 3 + maxArraySize(binds));
    }

    private static LdcInsnNode classLiteral(Type type) {
        return new LdcInsnNode(type);
    }

    private static void addClassArray(InsnList p, Type[] types) {
        pushInt(p, types.length);
        p.add(new TypeInsnNode(ANEWARRAY, "java/lang/Class"));
        for (int i = 0; i < types.length; i++) {
            p.add(new InsnNode(DUP));
            pushInt(p, i);
            p.add(classLiteral(types[i]));
            p.add(new InsnNode(AASTORE));
        }
    }

    private static void addStringArray(InsnList p, String[] values) {
        pushInt(p, values.length);
        p.add(new TypeInsnNode(ANEWARRAY, "java/lang/String"));
        for (int i = 0; i < values.length; i++) {
            p.add(new InsnNode(DUP));
            pushInt(p, i);
            p.add(new LdcInsnNode(values[i]));
            p.add(new InsnNode(AASTORE));
        }
    }

    private static void pushInt(InsnList p, int value) {
        if (value == 0) {
            p.add(new InsnNode(ICONST_0));
            return;
        }

        p.add(new LdcInsnNode(value));
    }

    private static int maxArraySize(List<Bind> binds) {
        int max = 0;
        for (Bind b : binds)
            max = Math.max(max, Type.getArgumentTypes(b.mn().desc).length);

        return max;
    }
}
