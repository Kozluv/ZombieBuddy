package me.zed_0xff.zombie_buddy.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.asm.Advice;

class Patch_Field_Test extends AbstractTest {
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    private @interface TestCase {
        String field();
    }

    protected static Stream<Arguments> provideClasses() {
        return withTransformersForClasses(Stream.of(Patch_Field_Test.class.getDeclaredClasses())
            .filter(c -> c.isAnnotationPresent(TestCase.class))
        );
    }

    static class Target1 {
        private int first;
        void getFoo() {}
    }

    @TestCase(field = "first")
    @Patch(className = "me.zed_0xff.zombie_buddy.transformers.Patch_Field_Test$Target1", methodName = "getFoo")
    static class Patch1 {
        static void m0(@Patch.Field int implicit) {}
        static void m1(@Patch.Field("renamed") int bar) {}
        static void m2(@Patch.Field({"first", "second"}) int bar) {}
    }

    static class Target2 {
        private int second;
        void getFoo() {}
    }

    @TestCase(field = "second")
    @Patch(className = "me.zed_0xff.zombie_buddy.transformers.Patch_Field_Test$Target2", methodName = "getFoo")
    static class Patch2 {
        static void m0(@Patch.Field int implicit) {}
        static void m1(@Patch.Field("renamed") int bar) {}
        static void m2(@Patch.Field({"first", "second"}) int bar) {}
    }

    @ParameterizedTest
    @MethodSource("provideClasses")
    void test_OnEnter(
            List<Class<? extends Transformer>> transformers,
            Class<?> patchCls
    ) throws Exception {
        TestCase tc = patchCls.getAnnotation(TestCase.class);
        var ctx = new TestClassContext(patchCls);
        byte[] bytes = ctx.getBytes();

        var p = ctx.getMethod("m1").getParameters().getOnly();
        assertThat(p.getDeclaredAnnotations()).hasSize(1);

        runTransformers(ctx, bytes, transformers);

        try {
            p = ctx.getMethod("m0").getParameters().getOnly();
            // assertThat(p.getDeclaredAnnotations()).hasSize(2);
            var a = p.getDeclaredAnnotations().ofType(Advice.FieldValue.class).load();
            assertThat(a.value()).isEqualTo("implicit");

            p = ctx.getMethod("m1").getParameters().getOnly();
            // assertThat(p.getDeclaredAnnotations()).hasSize(2);
            
            a = p.getDeclaredAnnotations().ofType(Advice.FieldValue.class).load();
            assertThat(a.value()).isEqualTo("renamed");

            p = ctx.getMethod("m2").getParameters().getOnly();
            // assertThat(p.getDeclaredAnnotations()).hasSize(2);

            a = p.getDeclaredAnnotations().ofType(Advice.FieldValue.class).load();
            assertThat(a.value()).isEqualTo(tc.field());
        } catch (Throwable t) {
            printDumps(runTransformers(ctx, bytes, transformers).dumps());
            throw t;
        }
    }
}
