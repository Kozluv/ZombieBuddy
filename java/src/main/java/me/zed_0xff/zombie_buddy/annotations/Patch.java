package me.zed_0xff.zombie_buddy.annotations;

import static me.zed_0xff.zombie_buddy.annotations.Internal.ZB_PREFIX;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ParameterNode;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.annotations.Internal.AnnElements;
import net.bytebuddy.asm.Advice;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Internal.MetaRoot
public @interface Patch {
    public static final String NAMEMAP_LOCAL_NAME = ZB_PREFIX + "nameMap";

    String className();
    String methodName();
    boolean isAdvice() default true;     // false => MethodDelegation
                                         // warning! advices can be chained, delegations can't, so only one delegation per method EVER
    boolean warmUp() default false;      // mandatory for some internal classes like LuaManager$Exposer or the patch will not be applied
                                         // boolean IKnowWhatIAmDoing() default false; // if true, the patch will be applied even if it is risky
    boolean strictMatch() default false; // if true, advice methods without arguments match only methods with no arguments
                                         // if false (default), advice methods without arguments match any method

    /** Alias for net.bytebuddy.asm.Advice.OnMethodEnter - mods should use Patch.OnEnter instead */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Internal.Meta
    public @interface OnEnter {
        boolean skipOn() default false;

        public class Converter extends Internal.AnnConverterBase {
            public AnnotationNode convert(AnnotationNode src, Object o) {
                var els = AnnElements.fromValues(src.values);
                return createNode(
                        Advice.OnMethodEnter.class,
                        Boolean.TRUE.equals(els.getBoolean("skipOn"))
                            ? new Object[] { "skipOn", Type.getType(Advice.OnNonDefaultValue.class) }
                            : null
                );
            }
        }
    }

    /** Alias for net.bytebuddy.asm.Advice.OnMethodExit - mods should use Patch.OnExit instead */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Internal.Meta(targetAnnotation = Advice.OnMethodExit.class)
    public @interface OnExit {
        Class<? extends Throwable> onThrowable() default NoException.class;
        Class<? extends Throwable> suppress() default NoException.class;
    }

    public static final String NO_EXCEPTION_DESC = "Lnet/bytebuddy/asm/Advice$NoExceptionHandler;"; // private class

    @Internal.Meta(targetAnnotationDesc = NO_EXCEPTION_DESC)
    public abstract static class NoException extends Throwable {}

    @Internal.Meta(targetAnnotation = Advice.OnDefaultValue.class)
    public abstract static class OnDefaultValue extends Throwable {}

    @Internal.Meta(targetAnnotation = Advice.OnNonDefaultValue.class)
    public abstract static class OnNonDefaultValue extends Throwable {}

    /** Binds the return value of the target method. {@code @Patch.OnExit} only. Use {@code readOnly = false} to overwrite it. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER) 
    @Internal.Meta(targetAnnotation = Advice.Return.class)
    public @interface Return {
        boolean readOnly() default true;
    }

    /** Binds the exception thrown by the target method, or {@code null} if none. {@code @Patch.OnExit} only. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = Advice.Thrown.class)
    public @interface Thrown {
        boolean readOnly() default true;
    }

    /** Binds the target object ({@code this}). Not available for static target methods. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = Advice.This.class)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.This.class, isAdvice = false)
    public @interface This {
        boolean readOnly() default true;
    }

    /** Binds the n-th argument (0-based) of the target method. Use {@code readOnly = false} to overwrite it.
     *  Set {@code optional = true} to bind null / the default primitive value when the index is out of range. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = Advice.Argument.class)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.Argument.class, isAdvice = false)
    public @interface Argument {
        int value() default 0;
        boolean readOnly() default true;
        boolean optional() default false;
    }

    /** Binds all target method arguments as {@code Object[]}. Use {@code readOnly = false} to overwrite them. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = Advice.AllArguments.class)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.AllArguments.class)
    public @interface AllArguments {
        boolean readOnly() default true;
    }

    /** Marks a {@code @Patch.OnEnter} / {@code @Patch.OnExit} method return type as dynamically typed (delegation only). */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.RuntimeType.class)
    public @interface RuntimeType {
    }

    /** Binds a {@code Method} handle to the overridden super-method (delegation only). */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.SuperMethod.class)
    public @interface SuperMethod {
    }

    /** Binds a {@code Callable} that invokes the overridden super-method (delegation only). */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = net.bytebuddy.implementation.bind.annotation.SuperCall.class)
    public @interface SuperCall {
    }

    /** Binds a named local variable of the target method. The variable must exist in the target's debug info. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta(targetAnnotation = Advice.Local.class)
    public @interface Local {
        String value();
    }

    /**
     * Binds a parameter to an instance or static field of the target class (read-only by default).
     *
     * <p>The field name is inferred from the parameter name when {@link #value()} is omitted,
     * which requires debug info (present in all standard Gradle builds).
     * Provide {@link #value()} explicitly when the parameter name differs from the field name.
     * Provide multiple names to try them in order; the first name that exists on the target class is used.
     * Multi-name resolution requires the target class to be already loaded, so it cannot be used in preload-time patches.
     *
     * <p>Use {@code readOnly = false} to write the (possibly modified) value back after the
     * advice returns, or prefer the shorthand {@link FieldRW}.
     *
     * <p>{@link #name()} is an alias for {@link #value()}; specifying both is a compile error.
     *
     * <pre>{@code
     * @Patch.OnEnter
     * public static void enter(@Patch.This Object self,
     *                          @Patch.Field String name,                      // inferred: reads this.name
     *                          @Patch.Field("counter") int c,                 // explicit field name
     *                          @Patch.Field({"speedNew", "speed"}) float spd) // tries "speedNew", falls back to "speed"
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Internal.Meta
    public @interface Field {
        @Internal.Flags(inferFromTargetName = true, probeField = true)
        String[] value() default {};                 // field name(s): empty = infer from parameter name; multiple = try in order
        Class<?> declaringType() default void.class; // the class that declares the field; void.class = infer from target class
        boolean readOnly() default true;
        boolean optional() default false;

        public class Converter extends Internal.AnnConverterBase {
            public AnnotationNode convert(AnnotationNode src, Object o) {
                if (!(o instanceof ParameterNode node))
                    throw new IllegalArgumentException("Expected ParameterNode, got " + o.getClass().getName());
                
                var els = AnnElements.fromValues(src.values);
                var names = els.getListStr("value");
                String name = null;
                if (Utils.isBlank(names)) {
                    name = node.name; // fallback to parameter name if 'name' element is missing or empty
                } else if (names.size() == 1) {
                    name = names.get(0);
                } else {
                    Logger.warn("Invalid @Patch.Field annotation: exactly one field name must be specified in 'name' element", src, names);
                    return null;
                }
                els.put("value", name);
                return createNode( Advice.FieldValue.class, els );
            }
        }
    }

    /**
     * Shorthand for {@code @Patch.Field(readOnly = false)}: binds a parameter to an instance or
     * static field of the target class and writes the value back after the advice returns.
     *
     * <p>The field name is inferred from the parameter name when {@link #value()} is omitted.
     * Multiple names may be specified and are tried in order against the target class.
     * Multi-name resolution requires the target class to be already loaded, so it cannot be used in preload-time patches.
     *
     * <pre>{@code
     * @Patch.OnEnter
     * public static void enter(@Patch.FieldRW int counter) {
     *     counter++;  // increments the field on the target object
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @Internal.Meta(targetAnnotation = Advice.FieldValue.class)
    public @interface FieldRW {
        @Internal.Flags(inferFromTargetName = true, probeField = true)
        String[] value() default {};  // empty = infer from parameter name; multiple = try in order
        Class<?> declaringType() default void.class; // the class that declares the field; void.class = infer from target class
        boolean optional() default false;
    }

    // https://javadoc.io/doc/net.bytebuddy/byte-buddy/1.18.8/net/bytebuddy/asm/Advice.Handle.html            - returns only MethodHandle, no VarHandle support
    // https://javadoc.io/doc/net.bytebuddy/byte-buddy/1.18.8/net/bytebuddy/asm/Advice.FieldGetterHandle.html - respects field visibility
    // https://javadoc.io/doc/net.bytebuddy/byte-buddy/1.18.8/net/bytebuddy/asm/Advice.FieldSetterHandle.html - --//--

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Internal.Meta(targetAnnotation = Advice.Handle.class, requireType = java.lang.invoke.MethodHandle.class)
    public @interface MethodHandle {
        @Internal.Flags(inferFromTargetName = true, probeField = true)
        String[] value()   default {};            // empty = infer from stub field name; multiple = try in order
        String className() default "";            // empty = infer from enclosing @Patch.className(); mutually exclusive with owner()
        Class<?> owner()   default void.class;    // type-safe alternative to className(); mutually exclusive with className()
        boolean optional() default false;         // false = drop patch class on missing field; true = leave field as null

        Class<?> returnType();
        Class<?>[] paramTypes() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Internal.Meta(requireType = java.lang.invoke.VarHandle.class)
    public @interface VarHandle {
        String[] name()    default {};            // empty = infer from stub field name; multiple = try in order
        String className() default "";            // empty = infer from enclosing @Patch.className(); mutually exclusive with owner()
        Class<?> owner()   default void.class;    // type-alternative to className(); mutually exclusive with className()
        boolean optional() default false;         // false = drop patch class on missing field; true = leave field as null

        Class<?> type();

        public class Converter extends Internal.AnnConverterBase {
            public AnnotationNode convert(AnnotationNode src, Object o) {
                if (!(o instanceof ParameterNode node))
                    throw new IllegalArgumentException("Expected ParameterNode, got " + o.getClass().getName());
                
                var els = AnnElements.fromValues(src.values);
                var names = els.getListStr("name");
                String name = null;
                if (Utils.isBlank(names)) {
                    name = node.name; // fallback to parameter name if 'name' element is missing or empty
                } else if (names.size() == 1) {
                    name = names.get(0);
                } else {
                    Logger.warn("Invalid @Patch.VarHandle annotation: exactly one field name must be specified in 'name' element", src, names);
                    return null;
                }
                return createNode(
                        Advice.Local.class,
                        "value", ZB_PREFIX + els.get("type").toString() + "|" + name
);
            }
        }
    }
}
