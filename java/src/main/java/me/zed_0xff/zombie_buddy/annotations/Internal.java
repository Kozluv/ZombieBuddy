package me.zed_0xff.zombie_buddy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Internal {
    public static final String ANN_PREFIX = "Lme/zed_0xff/zombie_buddy/annotations/";

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
        String[] targetParamNames() default {};
        String[] targetParamValues() default {};
        boolean isAdvice() default true;      // false => MethodDelegation
        Class<?>[] requireType() default {};
        boolean isRoot() default false;
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

        public static java.lang.invoke.MethodHandle getMethod(String key)   { return methodHandles.get(key); }
        public static java.lang.invoke.VarHandle    getVar(String key)      { return varHandles.get(key); }
        public static void putMethod(String key, java.lang.invoke.MethodHandle mh) { if (mh != null) methodHandles.put(key, mh); }
        public static void putVar(String key, java.lang.invoke.VarHandle vh)       { if (vh != null) varHandles.put(key, vh); }

        private HandleStore() {}
    }
}
