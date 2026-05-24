package me.zed_0xff.zombie_buddy.transformers;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.zed_0xff.zombie_buddy.annotations.Patch;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaConstant.MethodHandle.HandleType;

class Patch_MethodHandle_Test extends AbstractTest {
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    private @interface TestCase {
        String handleName();
        HandleType handleType();
        String handleOwner() default "";
        int intResult() default -1;
        String strResult() default "";
    }

    protected static Stream<Arguments> provideClasses() {
        return withTransformersForClasses(Stream.of(Patch_MethodHandle_Test.class.getDeclaredClasses())
            .filter(c -> c.isAnnotationPresent(TestCase.class))
        );
    }

    static class Target1 {
        public void getFoo() {}

        public int publicMethod()                    { return 42; }
        public static int publicStaticMethod()       { return 43; }
        private int privateMethod()                  { return 44; }
        private static int privateStaticMethod()     { return 45; }
        protected int protectedMethod()              { return 46; }
        protected static int protectedStaticMethod() { return 47; }

        public String methodWithArgs(String s)       { return s + s; }
        public int    methodWithArgs(int a, int b)   { return a + b; }
    }

    private static final String TARGET  = "me.zed_0xff.zombie_buddy.transformers.Patch_MethodHandle_Test$Target1";
    private static final String TARGET2 = "testjar.PackagePrivateTarget";

    @TestCase(handleName = "publicMethod", handleType = HandleType.INVOKE_VIRTUAL, intResult = 42)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPublic {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(returnType = int.class) MethodHandle publicMethod) throws Throwable {
            result = publicMethod.invoke(self);
            called = true;
        }
    }

    @TestCase(handleName = "publicStaticMethod", handleType = HandleType.INVOKE_STATIC, intResult = 43)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPublicStatic {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.MethodHandle(returnType = int.class) MethodHandle publicStaticMethod) throws Throwable {
            result = publicStaticMethod.invoke();
            called = true;
        }
    }

    @TestCase(handleName = "privateMethod", handleType = HandleType.INVOKE_SPECIAL, intResult = 44)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPrivate {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(returnType = int.class) MethodHandle privateMethod) throws Throwable {
            result = privateMethod.invoke(self);
            called = true;
        }
    }

    @TestCase(handleName = "privateMethod", handleType = HandleType.INVOKE_SPECIAL, intResult = 44)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchRenamed {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(name = "privateMethod", returnType = int.class) MethodHandle publicMethod) throws Throwable {
            result = publicMethod.invoke(self);
            called = true;
        }
    }

    @TestCase(handleName = "privateMethod", handleType = HandleType.INVOKE_SPECIAL, intResult = 44)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchRenamed3 {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(name = {"foo", "privateMethod", "bar"}, returnType = int.class) MethodHandle publicMethod) throws Throwable {
            result = publicMethod.invoke(self);
            called = true;
        }
    }

    @TestCase(handleName = "privateStaticMethod", handleType = HandleType.INVOKE_STATIC, intResult = 45)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPrivateStatic {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.MethodHandle(returnType = int.class) MethodHandle privateStaticMethod) throws Throwable {
            result = privateStaticMethod.invoke();
            called = true;
        }
    }

    @TestCase(handleName = "protectedMethod", handleType = HandleType.INVOKE_VIRTUAL, intResult = 46)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchProtected {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(returnType = int.class) MethodHandle protectedMethod) throws Throwable {
            result = protectedMethod.invoke(self);
            called = true;
        }
    }

    @TestCase(handleName = "protectedStaticMethod", handleType = HandleType.INVOKE_STATIC, intResult = 47)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchProtectedStatic {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.MethodHandle(returnType = int.class) MethodHandle protectedStaticMethod) throws Throwable {
            result = protectedStaticMethod.invoke();
            called = true;
        }
    }

    @TestCase(handleName = "methodWithArgs", handleType = HandleType.INVOKE_VIRTUAL, strResult = "abab")
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchArgs1 {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(returnType = String.class, paramTypes = { String.class }) MethodHandle methodWithArgs) throws Throwable {
            result = methodWithArgs.invoke(self, "ab");
            called = true;
        }
    }

    @TestCase(handleName = "methodWithArgs", handleType = HandleType.INVOKE_VIRTUAL, intResult = 5)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchArgs2 {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.This Target1 self, @Patch.MethodHandle(returnType = int.class, paramTypes = { int.class, int.class }) MethodHandle methodWithArgs) throws Throwable {
            result = methodWithArgs.invoke(self, 2, 3);
            called = true;
        }
    }

    @TestCase(handleName = "privateMethod", handleType = HandleType.INVOKE_SPECIAL, handleOwner = TARGET2, intResult = 99)
    @Patch(className = TARGET, methodName = "getFoo")
    static class PatchPackagePrivate {
        static boolean called;
        static Object result;

        @Patch.OnEnter
        static void m0(@Patch.MethodHandle(name = {"foo", "privateMethod", "bar"}, className = "testjar.PackagePrivateTarget", returnType = int.class) MethodHandle handle) throws Throwable {
            called = true;
        }
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

        var mhParam = ctx
            .getMethod("m0")
            .getParameters()
            .filter(p -> p.getDeclaredAnnotations().isAnnotationPresent(Patch.MethodHandle.class))
            .getOnly();
        assertThat(mhParam.getDeclaredAnnotations()).hasSize(1);

        var run = runTransformers(ctx, bytes, transformers);

        try {
            assertTransformed(run);

            mhParam = ctx.getMethod("m0")
                .getParameters()
                .filter(p -> p.getDeclaredAnnotations().isAnnotationPresent(Advice.Handle.class))
                .getOnly();
            assertThat(mhParam.getDeclaredAnnotations()).hasSize(2);
            var handle = mhParam.getDeclaredAnnotations().ofType(Advice.Handle.class).load();
            assertThat(handle.name()).isEqualTo(tc.handleName());
            assertThat(handle.type()).isEqualTo(tc.handleType());
            if (!tc.handleOwner().isEmpty()) {
                assertThat(handle.owner()).isEqualTo(Class.forName(tc.handleOwner()));
            }

            if (tc.handleOwner().isEmpty()) {
                Class<?> patchClass = applyAdviceAndCall(patchCls.getName(), run.bytes());
                assertPatchResult(patchClass, tc);
            }
        } catch (Throwable t) {
            printDumps(run.dumps());
            throw t;
        }
    }

    private static java.lang.reflect.Field patchField(Class<?> patchClass, String name) throws Exception {
        var field = patchClass.getDeclaredField(name);
        field.setAccessible(true);

        return field;
    }

    private static void resetPatchState(Class<?> patchClass) throws Exception {
        patchField(patchClass, "called").setBoolean(null, false);
        patchField(patchClass, "result").set(null, null);
    }

    private static void assertPatchResult(Class<?> patchClass, TestCase tc) throws Exception {
        assertThat(patchField(patchClass, "called").getBoolean(null)).isTrue();

        Object result = patchField(patchClass, "result").get(null);
        if (tc.strResult().isEmpty()) {
            assertThat(result).isEqualTo(tc.intResult());
        } else {
            assertThat(result).isEqualTo(tc.strResult());
        }
    }

    private static byte[] getNestedClassBytes(String className) throws IOException {
        String path = className.replace('.', '/') + ".class";

        try (var in = Patch_MethodHandle_Test.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing class bytes: " + path);

            return in.readAllBytes();
        }
    }

    private static Class<?> applyAdviceAndCall(String patchName, byte[] patchBytes) throws Exception {
        ClassLoader parent = Patch_MethodHandle_Test.class.getClassLoader();
        byte[] targetBytes = getNestedClassBytes(TARGET);
        ClassFileLocator locator = new ClassFileLocator.Compound(
            ClassFileLocator.Simple.of(patchName, patchBytes),
            ClassFileLocator.Simple.of(TARGET, targetBytes),
            ClassFileLocator.ForClassLoader.of(parent)
        );
        TypePool pool = TypePool.Default.of(locator);
        TypeDescription targetType = pool.describe(TARGET).resolve();
        TypeDescription patchType = pool.describe(patchName).resolve();
        byte[] instrumentedBytes = new ByteBuddy()
            .redefine(targetType, locator)
            .visit(Advice.to(patchType, locator).on(named("getFoo")))
            .make()
            .getBytes();

        ClassLoader isolated = new ByteArrayClassLoader.ChildFirst(
            parent,
            Map.of(TARGET, instrumentedBytes, patchName, patchBytes),
            ByteArrayClassLoader.PersistenceHandler.MANIFEST
        );
        Class<?> patchClass = isolated.loadClass(patchName);
        resetPatchState(patchClass);

        Class<?> targetClass = isolated.loadClass(TARGET);
        var ctor = targetClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object target = ctor.newInstance();
        var getFoo = targetClass.getDeclaredMethod("getFoo");
        getFoo.setAccessible(true);
        getFoo.invoke(target);

        return patchClass;
    }
}
