package me.zed_0xff.zombie_buddy.transformers;

import static me.zed_0xff.zombie_buddy.annotations.Internal.ZB_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.asm.Advice;

class Patch_VarHandle_Test extends AbstractTest {
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    private @interface TestCase {
        String field();
    }

    protected static Stream<Arguments> provideClasses() {
        return withTransformersForClasses(Stream.of(Patch_VarHandle_Test.class.getDeclaredClasses())
            .filter(c -> c.isAnnotationPresent(TestCase.class))
        );
    }

    static class Target1 {
        private int first;
        private int implicit;
        void getFoo() {}
    }

    private static final String TARGET = "me.zed_0xff.zombie_buddy.transformers.Resolver_Patch_VarHandle_Test$Target1";

    @TestCase(field = ZB_PREFIX + "I|implicit")
    @Patch(className = TARGET, methodName = "getFoo")
    static class Patch1 {
        static void m0(@Patch.VarHandle(type=int.class) VarHandle implicit) {}
    }

    @TestCase(field = ZB_PREFIX + "I|explicit")
    @Patch(className = TARGET, methodName = "getFoo")
    static class Patch2 {
        static void m0(@Patch.VarHandle(type=int.class, name="explicit") VarHandle vh) {}
    }

    @ParameterizedTest
    @MethodSource("provideClasses")
    void test_patch(
            List<Class<? extends Transformer>> transformers,
            Class<?> patchCls
    ) throws Exception {
        TestCase tc = patchCls.getAnnotation(TestCase.class);
        var ctx = new TestClassContext(patchCls);
        byte[] bytes = ctx.getBytes();

        var p = ctx.getMethod("m0").getParameters().getOnly();
        assertThat(p.getDeclaredAnnotations()).hasSize(1);

        var run = runTransformers(ctx, bytes, transformers);

        try {
            p = ctx.getMethod("m0").getParameters().getOnly();
            assertThat(p.getDeclaredAnnotations()).hasSize(2);
            var a = p.getDeclaredAnnotations().ofType(Advice.Local.class).load();
            assertThat(a.value()).isEqualTo(tc.field());
        } catch (Throwable t) {
            printDumps(run.dumps());
            throw t;
        }
    }
}
