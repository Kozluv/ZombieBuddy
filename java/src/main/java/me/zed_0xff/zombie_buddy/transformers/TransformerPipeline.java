package me.zed_0xff.zombie_buddy.transformers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Binder;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Converter;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Publicizer;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Resolver;
import me.zed_0xff.zombie_buddy.transformers.bytebuddy.ZB2Compat;

/** Shared transformer registry and jar/class pipeline (used by Loader and jardump). */
public final class TransformerPipeline {
    private enum TransOpt {
        CONDITIONAL,
        DEFAULT,
    }

    public record TransSpec(String id, Class<? extends Transformer> cls, String description, EnumSet<TransOpt> opts) {
        public TransSpec(String id, Class<? extends Transformer> cls, String description) {
            this(id, cls, description, EnumSet.noneOf(TransOpt.class));
        }

        public TransSpec(String id, Class<? extends Transformer> cls, String description, TransOpt... opts) {
            this(id, cls, description, EnumSet.copyOf(Arrays.asList(opts)));
        }
    }

    public record TransInfo(TransSpec spec, Supplier<Transformer> factory, boolean isDefault) {
        public String id()          { return spec.id(); }
        public Class<? extends Transformer> type() { return spec.cls(); }
        public String description() { return spec.description(); }
    }

    private static final TransSpec[] TRANS_SPECS = {
        new TransSpec("compat",   ZB2Compat.class,       "Convert ZB 2.x @Patch annotations", TransOpt.DEFAULT),
        new TransSpec("resolve",  Resolver.class,        "Resolve alternative names in annotations", TransOpt.DEFAULT),
        new TransSpec("convert",  Converter.class,       "Convert ZombieBuddy annotations to ByteBuddy annotations", TransOpt.DEFAULT),
        new TransSpec("bind",     Binder.class,          "Bind @Shadow classes", TransOpt.DEFAULT),
        new TransSpec("pub-all",  Publicizer.class,      "Publicize all members unconditionally"),
        new TransSpec("pub-cond", Publicizer.class,      "Publicize if any annotations were converted by the previous steps", TransOpt.CONDITIONAL, TransOpt.DEFAULT),
        new TransSpec("none",     NoopTransformer.class, "Do nothing (for testing/debugging purposes)"),
    };

    private static Supplier<Transformer> conditional(Supplier<? extends Transformer> factory) {
        return () -> new ConditionalTransformer(factory);
    }

    private static Supplier<Transformer> ctorSupplier(Class<? extends Transformer> cls) {
        return () -> {
            try {
                return cls.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        };
    }

    private static List<TransInfo> buildTransList() {
        ArrayList<TransInfo> out = new ArrayList<>();

        for (TransSpec spec : TRANS_SPECS) {
            Supplier<Transformer> factory = ctorSupplier(spec.cls());
            if (spec.opts().contains(TransOpt.CONDITIONAL)) {
                factory = conditional(factory);
            }

            out.add(new TransInfo(spec, factory, spec.opts().contains(TransOpt.DEFAULT)));
        }

        return List.copyOf(out);
    }

    private static final List<TransInfo> TRANS_LIST = buildTransList();

    public static final Map<String, TransInfo> TRANS_MAP =
        TRANS_LIST.stream().collect(Collectors.toMap(
                    TransInfo::id,
                    Function.identity(),
                    (a, b) -> b,
                    LinkedHashMap::new
                    ));

    public static List<String> defaultTransformerIds() {
        return TRANS_LIST.stream().filter(TransInfo::isDefault).map(TransInfo::id).toList();
    }

    public static List<TransInfo> transList() {
        return TRANS_LIST;
    }

    public static TransformerPipeline patchLoad() {
        return defaults();
    }

    public static TransformerPipeline defaults() {
        List<Supplier<Transformer>> factories = TRANS_LIST.stream().filter(TransInfo::isDefault).map(TransInfo::factory).toList();
        return new TransformerPipeline(factories);
    }

    public static TransformerPipeline of(List<String> ids) {
        ArrayList<Supplier<Transformer>> factories = new ArrayList<>();
        for (String id : ids) {
            TransInfo info = TRANS_MAP.get(id);
            if (info == null) throw new IllegalArgumentException("Unknown transformer: " + id);
            factories.add(info.factory());
        }

        return new TransformerPipeline(factories);
    }

    private final List<Supplier<Transformer>> m_factories;

    private TransformerPipeline(List<Supplier<Transformer>> factories) {
        m_factories = List.copyOf(factories);
    }

    public byte[] transformClass(String className, byte[] classBytes, JarContext jctx) {
        byte[] rewritten = classBytes.clone();
        ClassContext classCtx = new ClassContext(className, jctx);

        for (Supplier<Transformer> factory : m_factories) {
            Transformer t = factory.get();
            Transformer.Result result = t.transform(rewritten, classCtx);
            if (result.modified() && result.bytes() != null) {
                rewritten = result.bytes();
            }
        }

        return rewritten;
    }

    public void transformJarClasses(JarContext jctx, List<String> classNames) {
        for (String className : classNames) {
            byte[] classBytes = jctx.getClassBytes(className);
            if (classBytes == null) continue;

            jctx.setClassBytes(className, transformClass(className, classBytes, jctx));
        }
    }

    /** Transform {@code sourceJar} and return a cached copy under the game cache dir. */
    public static Path transformPatchJar(Path sourceJar) throws IOException {
        Path source = sourceJar.toRealPath();
        String hash = Utils.sha256Hex(source);
        Path cacheDir = patchJarCacheDir();
        Files.createDirectories(cacheDir);
        Path cached = cacheDir.resolve(hash + ".jar");
        if (Files.isRegularFile(cached)) {
            return cached;
        }

        Map<String, byte[]> classes = new HashMap<>();
        ArrayList<String> classNames = new ArrayList<>();
        LinkedHashMap<String, byte[]> otherEntries = new LinkedHashMap<>();
        Manifest manifest;

        try (JarFile jar = new JarFile(source.toFile(), false)) {
            manifest = jar.getManifest();
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                JarEntry je = en.nextElement();
                String name = je.getName();
                if (je.isDirectory()) continue;

                try (InputStream in = jar.getInputStream(je)) {
                    byte[] data = in.readAllBytes();
                    if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        classes.put(className, data);
                        classNames.add(className);
                    } else if (!JarFile.MANIFEST_NAME.equals(name)) {
                        otherEntries.put(name, data);
                    }
                }
            }
        }

        Collections.sort(classNames);
        TransformerPipeline pipeline = patchLoad();

        try (JarContext jctx = JarContext.forClasses(classes)) {
            pipeline.transformJarClasses(jctx, classNames);

            Path tmp = Files.createTempFile(cacheDir, "patch-", ".jar");
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tmp))) {
                if (manifest != null) {
                    jos.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
                    manifest.write(jos);
                    jos.closeEntry();
                }

                for (String className : classNames) {
                    byte[] bytes = jctx.getClassBytes(className);
                    if (bytes == null) continue;

                    JarEntry entry = new JarEntry(className.replace('.', '/') + ".class");
                    jos.putNextEntry(entry);
                    jos.write(bytes);
                    jos.closeEntry();
                }

                for (var e : otherEntries.entrySet()) {
                    jos.putNextEntry(new JarEntry(e.getKey()));
                    jos.write(e.getValue());
                    jos.closeEntry();
                }
            }

            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        Logger.debug("Transformed patch jar cached at " + cached);
        return cached;
    }

    private static Path patchJarCacheDir() {
        String cacheDir = Utils.getCacheDir();
        if (Utils.isBlank(cacheDir)) {
            return Path.of(System.getProperty("java.io.tmpdir"), "zombiebuddy", "patch-jars");
        }

        return Path.of(cacheDir, "zombiebuddy", "patch-jars");
    }
}
