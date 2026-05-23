package me.zed_0xff.zombie_buddy.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.zed_0xff.zombie_buddy.annotations.Shadow;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Resolver;

class Resolver_Shadow_Test extends AbstractTest {
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    private @interface TestCase {
        String field();
        boolean changed() default true;
    }

    static class Target {
        private int field1;
        private int field2;
        void method() {}
    }

    private static final String TARGET = "me.zed_0xff.zombie_buddy.transformers.Resolver_Shadow_Test$Target";

    @TestCase(field = "field1")
    @Shadow(className = TARGET)
    static class Shadow1 {
        @Shadow.Field int field1;
    }

    @TestCase(field = "field1", changed = false)
    @Shadow(className = TARGET)
    static class Shadow2 {
        @Shadow.Field("field1") int x;
    }

    @TestCase(field = "field1")
    @Shadow(className = TARGET)
    static class Shadow3 {
        @Shadow.Field({"field1", "field2"}) int x;
    }

    @TestCase(field = "field2")
    @Shadow(className = TARGET)
    static class Shadow4 {
        @Shadow.Field({"xx", "field2"}) int x;
    }

    @TestCase(field = "field1")
    @Shadow(className = TARGET)
    static class Shadow5 {
        @Shadow.Field({"field1", "xx"}) int x;
    }

    @TestCase(field = "field2", changed = false)
    @Shadow(className = TARGET)
    static class TrickyShadow1 {
        @Shadow.Field("field2") int field1;
    }

    @TestCase(field = "xxfield", changed = true)
    @Shadow(className = TARGET)
    static class BadShadow1 {
        @Shadow.Field int xxfield;
    }

    @TestCase(field = "field1", changed = false)
    @Shadow(className = TARGET)
    static class BadShadow2 {
        @Shadow.Field("xx") int field1;
    }

    protected static Stream<Arguments> provideClasses() {
        return Stream.of(Resolver_Shadow_Test.class.getDeclaredClasses())
            .filter(c -> c.isAnnotationPresent(TestCase.class))
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("provideClasses")
    void test(Class<?> cls) throws Exception {
        TestCase tc = cls.getAnnotation(TestCase.class);
        var ctx = new TestClassContext(cls);
        byte[] bytes = ctx.getBytes();

        var run = runTransformer(ctx, bytes, Resolver.class);
        try {
            if (tc.changed()) {
                assertTransformed(run);
                assertThat(run.bytes()).isNotNull();
            } else {
                assertThat(run.modified()).isFalse();
            }
        } catch (Throwable t) {
            printDumps(run.dumps());
            throw t;
        }

    }
}
