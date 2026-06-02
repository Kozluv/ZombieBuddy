package me.zed_0xff.zombie_buddy.transformers;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.Collections;
import java.util.stream.Collectors;

import me.zed_0xff.zombie_buddy.CLIUtil;
import me.zed_0xff.zombie_buddy.Logger;
import me.zed_0xff.zombie_buddy.Utils;
import me.zed_0xff.zombie_buddy.annotations.Patch;
import me.zed_0xff.zombie_buddy.jardump.AsmDump;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Binder;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Converter;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Publicizer;
import me.zed_0xff.zombie_buddy.transformers.asmtree.Resolver;
import me.zed_0xff.zombie_buddy.transformers.bytebuddy.ZB2Compat;

/** Shared transformer registry and jar/class pipeline (used by Loader and jardump). */
public final class Pipeline {
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

    public static Pipeline patchLoad() {
        return defaults();
    }

    public static Pipeline defaults() {
        List<Supplier<Transformer>> factories = TRANS_LIST.stream().filter(TransInfo::isDefault).map(TransInfo::factory).toList();
        return new Pipeline(factories);
    }

    public static Pipeline of(List<String> ids) {
        ArrayList<Supplier<Transformer>> factories = new ArrayList<>();
        for (String id : ids) {
            TransInfo info = TRANS_MAP.get(id);
            if (info == null) throw new IllegalArgumentException("Unknown transformer: " + id);
            factories.add(info.factory());
        }

        return new Pipeline(factories);
    }

    private final List<Supplier<Transformer>> m_factories;

    private Pipeline(List<Supplier<Transformer>> factories) {
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

                if (Logger.getLevel() >= Logger.TRACE || classCtx.isDebug()) {
                    AsmDump dumper = new AsmDump(jctx);
                    System.err.println(t.getClass().getSimpleName() + ":");
                    System.err.println(CLIUtil.indent(dumper.dumpClass(rewritten)));
                }
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

    /** Transform {@code sourceJar} in memory (patch-load pipeline). */
    public static TransformedJar transformPatchJar(java.nio.file.Path sourceJar, String packageName) throws IOException {
        return transformPatchJar(java.nio.file.Files.readAllBytes(sourceJar.toRealPath()), packageName);
    }

    /** Transform embedded patch-jar bytes in memory (patch-load pipeline). */
    public static TransformedJar transformPatchJar(byte[] jarBytes, String packageName) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        ArrayList<String> classNames = new ArrayList<>();
        LinkedHashMap<String, byte[]> resources = new LinkedHashMap<>();
        Manifest manifest;

        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            manifest = jar.getManifest();
            JarEntry je;
            while ((je = jar.getNextJarEntry()) != null) {
                String name = je.getName();
                if (je.isDirectory()) continue;

                byte[] data = jar.readAllBytes();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classes.put(className, data);
                    classNames.add(className);
                } else if (!JarFile.MANIFEST_NAME.equals(name)) {
                    resources.put(name, data);
                }
            }
        }

        return transformParsedJar(classes, classNames, resources, manifest, packageName);
    }

    private static TransformedJar transformParsedJar(
            Map<String, byte[]> classes,
            ArrayList<String> classNames,
            LinkedHashMap<String, byte[]> resources,
            Manifest manifest,
            String packageName
    ) throws IOException {

        Pipeline pipeline = patchLoad();

        try (JarContext jctx = JarContext.forClasses(classes)) {
            pipeline.transformJarClasses(jctx, classNames);

            HashMap<String, byte[]> transformedClasses = new HashMap<>(classes.size());
            for (String className : classNames) {
                byte[] bytes = jctx.getClassBytes(className);
                if (bytes != null) {
                    transformedClasses.put(className, bytes);
                }
            }

            if (manifest != null) {
                ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
                manifest.write(manifestOut);
                resources.putIfAbsent(JarFile.MANIFEST_NAME, manifestOut.toByteArray());
            }

            ArrayList<String> patches = new ArrayList<>();
            for (String className : classNames) {
                var td = jctx.getTypeDesc(className);
                if (td == null || td.getDeclaredAnnotations().ofType(Patch.class) == null) {
                    continue;
                }

                if (!Utils.isBlank(packageName)) {
                    String classPackage = td.getPackage() != null ? td.getPackage().getName() : "";
                    if (!classPackage.equals(packageName)) {
                        continue;
                    }
                }

                patches.add(className);
            }
            Collections.sort(patches);

            String mainClassName = null;
            String preMainClassName = null;
            if (!Utils.isBlank(packageName)) {
                String main = packageName + ".Main";
                String preMain = packageName + ".PreMain";
                if (transformedClasses.containsKey(main)) {
                    mainClassName = main;
                }
                if (transformedClasses.containsKey(preMain)) {
                    preMainClassName = preMain;
                }
            }

            return new TransformedJar(Map.copyOf(transformedClasses), Map.copyOf(resources), List.copyOf(patches), mainClassName, preMainClassName);
        }
    }
}
