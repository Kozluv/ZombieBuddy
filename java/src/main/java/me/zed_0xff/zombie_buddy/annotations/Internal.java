package me.zed_0xff.zombie_buddy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import me.zed_0xff.zombie_buddy.Utils;

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
        boolean isAdvice() default true;      // false => MethodDelegation
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
        AnnotationNode convert(AnnotationNode src, Object node);

        public static AnnotationNode createNode(Class<?> cls, Object... values) {
            AnnotationNode node = new AnnotationNode(Type.getDescriptor(cls));
            if (values != null) {
                node.values = List.of(values);
            }
            return node;
        }
    }

    public interface FieldAnnConverter  { AnnotationNode convert(AnnotationNode src, FieldNode node); }
    public interface MethodAnnConverter { AnnotationNode convert(AnnotationNode src, MethodNode node); }
    public interface ParamAnnConverter  { AnnotationNode convert(AnnotationNode src, ParameterNode node); }

    public static abstract class AnnConverterBase implements AnnConverter {
        public static AnnotationNode createNode(Class<?> cls, Object... values) {
            AnnotationNode node = new AnnotationNode(Type.getDescriptor(cls));
            if (!Utils.isBlank(values)) {
                if (values[0] instanceof AnnElements els) {
                    node.values = els.toValues();
                } else {
                    node.values = List.of(values);
                }
            }
            return node;
        }
    }

    static class AnnElements extends HashMap<String, Object> {
        public static AnnElements fromValues(List<Object> values) {
            if (Utils.isBlank(values)) return new AnnElements();

            AnnElements map = new AnnElements();
            for (int i = 0; i < values.size(); i += 2) {
                map.put((String)values.get(i), values.get(i + 1));
            }
            return map;
        }

        public List<Object> toValues() {
            if (Utils.isBlank(this)) return List.of();

            return this.entrySet().stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toList();
        }

        /** ASM runtime may store annotation booleans as {@link Integer} ({@code 0}/{@code 1}) instead of {@link Boolean}. */
        public Boolean getBoolean(String name) {
            Object val = get(name);
            if (val instanceof Boolean b) return b;
            if (val instanceof Integer j) return j != 0;

            return null;
        }

        public List<String> getListStr(String name) {
            Object val = get(name);
            if (val instanceof List<?> list) {
                return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
            return null;
        }
    }
}
