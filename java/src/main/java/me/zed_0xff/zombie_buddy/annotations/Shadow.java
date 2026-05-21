package me.zed_0xff.zombie_buddy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE})
public @interface Shadow {
    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Field {
        @Internal.Flags(inferFromTargetName = true, probeField = true)
        String[] value() default {};                 // field name(s): empty = infer from parameter name; multiple = try in order
        @Internal.Flags(targetElement = "value")
        String[] name() default {};                  // alias for value()
        Class<?> declaringType() default void.class; // the class that declares the field; void.class = infer from target class
        boolean readOnly() default true;
        boolean optional() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Method {
        @Internal.Flags(inferFromTargetName = true, probeMethod = true)
        String[] value() default {};  // empty = infer from parameter name; multiple = try in order
    }

    /*
     * drop method body and short-circuit argument to return value
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Intrinsic {
    }
}
