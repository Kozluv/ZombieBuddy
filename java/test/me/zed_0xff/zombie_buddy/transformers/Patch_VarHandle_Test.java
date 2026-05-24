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
        public int publicField;
        public static int publicStaticField;
        private int privateField;
        private static int privateStaticField;
        protected int protectedField;
        protected static int protectedStaticField;
        private String stringField;
        private int implicit;

        void getFoo() {}
    }

    private static final String TARGET  = "me.zed_0xff.zombie_buddy.transformers.Patch_VarHandle_Test$Target1";
    private static final String TARGET2 = "testjar.VarHandleTarget";

    @TestCase(field = ZB_PREFIX + "I|implicit")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchImplicit {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle implicit) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchExplicit {
        static void m0(@Patch.VarHandle(type = int.class, name = "privateField") VarHandle vh) {}
    }

    @TestCase(field = ZB_PREFIX + "I|publicField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPublic {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle publicField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|publicStaticField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPublicStatic {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle publicStaticField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPrivate {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle privateField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateStaticField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPrivateStatic {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle privateStaticField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|protectedField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchProtected {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle protectedField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|protectedStaticField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchProtectedStatic {
        static void m0(@Patch.VarHandle(type = int.class) VarHandle protectedStaticField) {}
    }

    @TestCase(field = ZB_PREFIX + "Ljava/lang/String;|stringField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchString {
        static void m0(@Patch.VarHandle(type = String.class) VarHandle stringField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchRenamed {
        static void m0(@Patch.VarHandle(type = int.class, name = "privateField") VarHandle publicField) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchProbed {
        static void m0(@Patch.VarHandle(type = int.class, name = { "foo", "privateField", "bar" }) VarHandle vh) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchCrossClassName {
        static void m0(@Patch.VarHandle(type = int.class, name = { "foo", "privateField", "bar" }, className = TARGET2) VarHandle vh) {}
    }

    @TestCase(field = ZB_PREFIX + "I|privateField")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchCrossClassExplicit {
        static void m0(@Patch.VarHandle(type = int.class, name = "privateField", className = TARGET2) VarHandle vh) {}
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
            var local = vhParam.getDeclaredAnnotations().ofType(Advice.Local.class).load();
            assertThat(local.value()).isEqualTo(tc.field());
        } catch (Throwable t) {
            printDumps(run.dumps());
            throw t;
        }
    }
}
