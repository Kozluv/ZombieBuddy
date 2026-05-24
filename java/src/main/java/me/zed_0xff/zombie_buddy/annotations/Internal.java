package me.zed_0xff.zombie_buddy.annotations;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ParameterNode;

import me.zed_0xff.zombie_buddy.Reflect;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.transformers.AnnElements;
import me.zed_0xff.zombie_buddy.transformers.ClassContext;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaConstant.MethodHandle.HandleType;

public final class Internal {
    public static final String ANN_PREFIX = "Lme/zed_0xff/zombie_buddy/annotations/";
    public static final String ZB_PREFIX  = "zb|";

    private Internal() {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface MetaRoot {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Metas {
        Meta[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Metas.class)
    public @interface Meta {
        Class<?> targetAnnotation() default void.class;
        String targetAnnotationDesc() default "";       // for mapping to private classes
        boolean isAdvice() default true;                // false => MethodDelegation
        Class<?>[] requireType() default {};
    }

    public static final class DropAnnParam {} // drop annotation parameter

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MapBool {
        Class<?> onTrue();
        Class<?> onFalse() default DropAnnParam.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Flags {
        String targetElement() default "";
        boolean inferFromTargetName() default false;
        boolean probeField() default false;
        boolean probeMethod() default false;
    }

    /** Runtime registry for field-name resolution maps bound via {@code @Patch.NameMap} parameters.
     *  Populated by PatchTransformer at instrumentation time; read by inlined advice bytecode. */
    public static final class NameStore {
        private static final ConcurrentHashMap<String, Map<String, String>> store = new ConcurrentHashMap<>();

        public static Map<String, String> get(String key) { return store.get(key); }
        public static void put(String key, Map<String, String> map) { if (map != null) store.put(key, map); }

        private NameStore() {}
    }

    /** Runtime registry for method handles bound via parameter-level {@code @Patch.MemberHandle}.
     *  Populated by PatchTransformer at instrumentation time; read by inlined advice bytecode. */
    public static final class HandleStore {
        private static final ConcurrentHashMap<String, java.lang.invoke.MethodHandle> methodHandles = new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<String, java.lang.invoke.VarHandle>    varHandles    = new ConcurrentHashMap<>();

        public static java.lang.invoke.MethodHandle getMethod(String key)          { return methodHandles.get(key); }
        public static java.lang.invoke.VarHandle    getVar(String key)             { return varHandles.get(key); }
        public static void putMethod(String key, java.lang.invoke.MethodHandle mh) { if (mh != null) methodHandles.put(key, mh); }
        public static void putVar(String key, java.lang.invoke.VarHandle vh)       { if (vh != null) varHandles.put(key, vh); }

        private HandleStore() {}
    }

    // TODO: remove
    public interface AnnConverter {
        AnnotationNode convert(AnnotationNode src, Object node, ClassContext ctx) throws Throwable;
    }

    public static abstract class AnnConverterBase implements AnnConverter {
        public static AnnotationNode createNode(Class<?> cls, Object... values) {
            AnnotationNode node = new AnnotationNode(Type.getDescriptor(cls));
            if (!Utils.isBlank(values)) {
                if (values[0] instanceof AnnElements els) {
                    node.values = els.toValues();
                } else {
                    if (values.length % 2 != 0) throw new IllegalArgumentException("Annotation values should be in key-value pairs");

                    ArrayList<Object> filtered = new ArrayList<>();
                    for (int i = 0; i < values.length; i+=2) {
                        Object key = values[i];
                        Object val = values[i + 1];
                        if (Utils.isBlank(key) || val == null) continue; // empty string for value is acceptable
                        if (!(key instanceof String)) throw new IllegalArgumentException("Annotation value keys should be strings, got: (" + key.getClass() + ") " + key);

                        filtered.add(key); filtered.add(normalizeAnnValue(val));
                    }
                    if (!filtered.isEmpty())
                        node.values = filtered;
                }
            }
            return node;
        }

        /** ASM class literals use {@link Type}; enum literals use {@code String[2]}; class arrays use {@link List}. */
        private static Object normalizeAnnValue(Object val) {
            if (val instanceof Class<?> c) return Type.getType(c);
            if (val instanceof Enum<?> e) return new String[] { Type.getDescriptor(e.getClass()), e.name() };
            if (val instanceof List<?> list) return normalizeAnnList(list);
            if (val != null && val.getClass().isArray()) {
                if (val.getClass().getComponentType() == String.class) return val;

                return normalizeAnnArray(val);
            }

            return val;
        }

        private static List<Object> normalizeAnnList(List<?> list) {
            if (Utils.isBlank(list)) return List.of();

            ArrayList<Object> out = new ArrayList<>(list.size());
            for (Object elem : list) {
                out.add(elem instanceof Class<?> c ? Type.getType(c) : elem);
            }

            return out;
        }

        private static List<Type> normalizeAnnArray(Object array) {
            int len = Array.getLength(array);
            if (len == 0) return List.of();

            if (!(Array.get(array, 0) instanceof Class<?>)) {
                throw new IllegalArgumentException("Unsupported annotation array element type: " + Array.get(array, 0).getClass());
            }

            ArrayList<Type> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(Type.getType((Class<?>) Array.get(array, i)));
            }

            return out;
        }

        private static final MethodHandle mh_HandleType_of = Reflect
            .on(HandleType.class)
            .getMethodHandle(HandleType.class, new Class<?>[]{ MethodDescription.InDefinedShape.class }, "of");

        public static Type resolveHandleOwnerAnn(AnnElements els) {
            Object ownerVal = els.get("owner");
            if (ownerVal instanceof Type t && t.getSort() == Type.OBJECT) return t;

            Object cn = els.get("className");
            if (cn instanceof String className && !Utils.isBlank(className)) return Type.getObjectType(className.replace('.', '/'));

            return null;
        }

        public static TypeDescription resolveHandleOwner(ClassContext ctx, AnnElements els) {
            Type ownerAnn = resolveHandleOwnerAnn(els);
            if (ownerAnn != null) return ctx.jarContext().getOrigTypeDesc(ownerAnn.getClassName());

            return ctx.getTarget();
        }

        public static HandleType resolveHandleType(TypeDescription target, String name, Type returnType, List<Type> paramTypes) throws Throwable {
            if (target == null) throw new IllegalStateException("patch target type unavailable for @Patch.MethodHandle");

            for (MethodDescription method : target.getDeclaredMethods().filter(named(name))) {
                if (!typeDescEquals(method.getReturnType().asErasure(), returnType)) continue;
                if (method.getParameters().size() != paramTypes.size()) continue;

                boolean paramsMatch = true;
                for (int i = 0; i < paramTypes.size(); i++) {
                    if (!typeDescEquals(method.getParameters().get(i).getType().asErasure(), paramTypes.get(i))) {
                        paramsMatch = false;
                        break;
                    }
                }

                if (!paramsMatch) continue;

                return (HandleType) mh_HandleType_of.invokeExact((MethodDescription.InDefinedShape) method.asDefined());
            }

            throw new IllegalArgumentException("target method not found: " + name + " " + returnType + " " + paramTypes);
        }

        public static Type requireAnnType(Object val) {
            if (val instanceof Type t) return t;

            throw new IllegalArgumentException("expected ASM Type, got: " + val);
        }

        public static List<Type> annTypeList(Object val) {
            if (val == null) return List.of();
            if (!(val instanceof List<?> list)) throw new IllegalArgumentException("expected List<Type>, got: " + val);

            ArrayList<Type> out = new ArrayList<>(list.size());
            for (Object elem : list) {
                out.add(requireAnnType(elem));
            }

            return out;
        }

        private static boolean typeDescEquals(TypeDescription td, Type asmType) {
            return td.getDescriptor().equals(asmType.getDescriptor());
        }

        public static String resolveSingleName(AnnElements els, ParameterNode node, String listKey) {
            var names = els.getListStr(listKey);
            if (Utils.isBlank(names)) return node.name;
            if (names.size() == 1) return names.get(0);

            return null;
        }
    }
}
