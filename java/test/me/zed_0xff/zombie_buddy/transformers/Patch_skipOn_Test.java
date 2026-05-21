package me.zed_0xff.zombie_buddy.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.zed_0xff.zombie_buddy.annotations.Patch;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Converter;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Resolver;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;

class Patch_skipOn_Test extends AbstractTest {
    protected static Stream<Arguments> provideClasses() {
        List<List<Class<? extends Transformer>>> converters = List.of(
            List.of(Converter.class, Resolver.class),
            List.of(Resolver.class, Converter.class)
        );

        List<Class<?>[]> patches = List.of(
            new Class<?>[]{ Patch1.class, void.class },
            new Class<?>[]{ Patch2.class, Advice.OnNonDefaultValue.class },
            new Class<?>[]{ Patch3.class, void.class }
        );

        return converters.stream().flatMap(c ->
            patches.stream().map(p ->
                Arguments.of(
                    c,
                    p[0], p[1]
                )
            )
        );
    }

    private static class Target {
    }

    private static final String TARGET = "me.zed_0xff.zombie_buddy.transformers.Patch_skipOn_Test$Target";

    @Patch(className = TARGET, methodName = "getFoo")
    static class Patch1 {
        @Patch.OnEnter
        static void m1() {}
    }

    @Patch(className = TARGET, methodName = "getFoo")
    static class Patch2 {
        @Patch.OnEnter(skipOn = true)
        static boolean m1() { return true; }
    }

    @Patch(className = TARGET, methodName = "getFoo")
    static class Patch3 {
        @Patch.OnEnter(skipOn = false)
        static boolean m1() { return true; }
    }

    @ParameterizedTest
    @MethodSource("provideClasses")
    void test_OnEnter(
            List<Class<? extends Transformer>> transformers,
            Class<?> patchCls,
            Class<?> resultCls
    ) throws Exception {
        ArrayList<String> dumps = new ArrayList<>();
        var ctx = new TestClassContext(patchCls);
        byte[] bytes = ctx.getBytes();

        var m = ctx.getMethod("m1");
        assertThat(m.getDeclaredAnnotations())
            .hasSize(1);

        for (Class<? extends Transformer> transformerCls : transformers) {
            Transformer transformer = transformerCls.getDeclaredConstructor().newInstance();
            var result = transformer.transform(bytes, ctx);
            if (result.modified()) {
                bytes = result.bytes();
                dumps.add(ctx.dumpClass(bytes));
            }
        }

        try {
            m = ctx.getMethod("m1");
            assertThat(m.getDeclaredAnnotations())
                .hasSize(2);

            var a = m.getDeclaredAnnotations().filter(x -> x.getAnnotationType().isAssignableTo(Advice.OnMethodEnter.class)).getOnly();
            assertThat(a.getValue("skipOn").resolve())
                .isEqualTo(TypeDescription.ForLoadedType.of(resultCls));
        } catch (Throwable t) {
            dumps.forEach(d -> { System.out.println(d); });
            throw t;
        }
    }
}
