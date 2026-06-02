package me.zed_0xff.zombie_buddy.transformers;

import static me.zed_0xff.zombie_buddy.CLIUtil.BRIGHT;
import static me.zed_0xff.zombie_buddy.CLIUtil.CYAN;
import static me.zed_0xff.zombie_buddy.CLIUtil.RED;
import static me.zed_0xff.zombie_buddy.CLIUtil.colorize;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.jardump.AsmDump;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Converter;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Resolver;
import net.bytebuddy.description.method.MethodDescription;

public abstract class AbstractTest {
    protected static final List<List<Class<? extends Transformer>>> DEFAULT_TRANSFORMERS = List.of(
        List.of(Resolver.class, Converter.class)
    );

    protected record TransformRun(byte[] bytes, List<String> dumps, boolean modified, String transformerNames) {}

    protected static Stream<Arguments> withTransformers(List<Object[]> cases) {
        return DEFAULT_TRANSFORMERS.stream().flatMap(transformers ->
            cases.stream().map(c -> {
                Object[] args = new Object[c.length + 1];
                args[0] = transformers;
                System.arraycopy(c, 0, args, 1, c.length);
                return Arguments.of(args);
            })
        );
    }

    protected static Stream<Arguments> withTransformersForClasses(Stream<Class<?>> classes) {
        return withTransformers(classes.map(c -> new Object[] { c }).toList());
    }

    protected static Stream<Arguments> withTransformer(Class<? extends Transformer> transformer) {
        return Stream.of(Arguments.of(transformer));
    }

    protected static TransformRun runTransformers(TestClassContext ctx, byte[] bytes, List<Class<? extends Transformer>> transformers) throws Exception {
        ArrayList<String> dumps = new ArrayList<>();
        dumps.add(dump("initial", ctx, bytes));
        boolean modified = false;
        String names = transformers.stream().map(Class::getSimpleName).toList().toString();

        for (Class<? extends Transformer> transformerCls : transformers) {
            Transformer transformer = transformerCls.getDeclaredConstructor().newInstance();
            Transformer.Result result;
            try {
                result = transformer.transform(bytes, ctx);
            } catch (Throwable t) {
                printDumps(dumps);
                throw t;
            }

            if (result.modified()) {
                modified = true;
                bytes = result.bytes();
                dumps.add(dump(transformerCls.getSimpleName(), ctx, bytes));
            } else {
                dumps.add(dump(transformerCls.getSimpleName(), ctx, null));
            }
        }

        return new TransformRun(bytes, dumps, modified, names);
    }

    protected static TransformRun runTransformer(TestClassContext ctx, byte[] bytes, Class<? extends Transformer> transformer) throws Exception {
        return runTransformers(ctx, bytes, List.of(transformer));
    }

    private static String dump(String label, TestClassContext ctx, byte[] bytes) {
        return "\n" + colorize("[" + label + "]", CYAN + BRIGHT) + "\n" + (bytes == null ? "(no change)" : ctx.dumpClass(bytes));
    }

    protected static void printDumps(List<String> dumps) {
        dumps.forEach(System.out::println);
    }

    protected static void assertTransformed(TransformRun run) {
        if (run.modified()) return;

        fail(colorize("expected " + run.transformerNames() + " to transform the class but it didn't", RED + BRIGHT));
    }

    protected static byte[] getClassBytes(Class<?> cls) throws IOException {
        String path = "/" + cls.getName().replace('.', '/') + ".class";

        try (var in = cls.getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    public static class TestClassContext extends ClassContext {
        private byte[] m_bytes;

        public TestClassContext(Class<?> cls) throws IOException {
            super(cls.getName(), JarContext.forClass(cls.getName(), AbstractTest.getClassBytes(cls)));
            m_bytes = AbstractTest.getClassBytes(cls);
        }

        public byte[] getBytes() { return m_bytes; }

        public MethodDescription getMethod(String name) {
            var match = getCurrentTypeDesc().getDeclaredMethods().filter(named(name));
            if (match.size() == 0) return null;
            if (match.size() == 1) return match.getOnly();

            Logger.warn("Multiple methods found. Returning first match for", name);
            return match.get(0);
        }

        public String dumpClass(byte[] bytes) {
            AsmDump dumper = new AsmDump(jarContext());
            dumper.setSimpleNames(false);
            return dumper.dumpClass(bytes);
        }
    }
}
