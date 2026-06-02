package me.zed_0xff.zombie_buddy.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
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

class Patch_This_Test extends AbstractTest {
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    private @interface TestCase {
        Class<? extends Annotation> annClass();
    }

    protected static Stream<Arguments> provideClasses() {
        return withTransformersForClasses(Stream.of(Patch_This_Test.class.getDeclaredClasses())
            .filter(c -> c.isAnnotationPresent(TestCase.class))
        );
    }

    static class Target1 {
        void getFoo() {}
    }

    private static final String TARGET  = "me.zed_0xff.zombie_buddy.transformers.Patch_This_Test$Target1";
    private static final String TARGET2 = "testjar.ThisTarget";

    @TestCase(annClass = Advice.This.class)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchAdvice {
        static void m0(@Patch.This Object self) {}
    }

    @TestCase(annClass = net.bytebuddy.implementation.bind.annotation.This.class)
    @Patch(className = TARGET, methodName = "getFoo", isAdvice = false)
    static class PatchMDeleg {
        static void m0(@Patch.This Object self) {}
    }

    @ParameterizedTest
    @MethodSource("provideClasses")
    void test_patch(
            List<Class<? extends Transformer>> transformers,
            Class<?> patchCls
    ) throws Exception {
        TestCase tc = patchCls.getAnnotation(TestCase.class);
        assertThat(tc).isNotNull();

        var ctx = new TestClassContext(patchCls);
        byte[] bytes = ctx.getBytes();

        var vhParam = ctx.getMethod("m0").getParameters().getOnly();
        assertThat(vhParam.getDeclaredAnnotations()).hasSize(1);

        var run = runTransformers(ctx, bytes, transformers);

        try {
            assertTransformed(run);

            vhParam = ctx.getMethod("m0").getParameters().getOnly();
            assertThat(vhParam.getDeclaredAnnotations()).hasSize(2);
            var ann = vhParam.getDeclaredAnnotations().ofType(tc.annClass()).load();
            assertThat(ann).isNotNull();
        } catch (Throwable t) {
            printDumps(run.dumps());
            throw t;
        }
    }
}
