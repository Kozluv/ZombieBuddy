package me.zed_0xff.zombie_buddy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import me.zed_0xff.zombie_buddy.transformers.TransformedJar;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassInjector;

/** Defines transformed mod jar classes in memory via {@link ByteArrayClassLoader}; no temp file or {@link java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch}. */
final class ModJarInjector {
    private ModJarInjector() {}

    static ClassLoader inject(TransformedJar jar) {
        // Parent must be the agent loader so patch jars can see shaded deps (e.g. zb.com.google.gson).
        ClassLoader parent = ZombieBuddy.class.getClassLoader();
        if (parent == null) {
            parent = Thread.currentThread().getContextClassLoader();
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }

        HashMap<String, byte[]> definitions = new HashMap<>(jar.classes());
        byte[] manifest = jar.resources().get(JarFile.MANIFEST_NAME);
        if (manifest != null) {
            definitions.put(JarFile.MANIFEST_NAME, manifest);
        }

        return new ByteArrayClassLoader.ChildFirst(parent, definitions, ByteArrayClassLoader.PersistenceHandler.MANIFEST);
    }

    /**
     * Define patch-related mod classes in {@code targetLoader} (game loader) so woven Advice can call them.
     * Only {@link TransformedJar#patches()} plus nested patch types and other classes in the same package(s);
     * vendored deps (e.g. fat-jarred {@code org.lwjgl.*}) stay on the mod loader only.
     */
    static void exposePatchesInClassLoader(TransformedJar jar, ClassLoader targetLoader) {
        if (jar == null || targetLoader == null || jar.classes().isEmpty() || jar.patches().isEmpty()) {
            return;
        }

        if (!ClassInjector.UsingUnsafe.isAvailable()) {
            Logger.error("ClassInjector.UsingUnsafe not available; cannot expose patch classes to " + targetLoader);
            return;
        }

        Set<String> patchRoots = new HashSet<>(jar.patches());
        Set<String> packagePrefixes = packagesForPatches(jar.patches());
        HashMap<String, byte[]> toInject = new HashMap<>();

        for (var entry : jar.classes().entrySet()) {
            String name = entry.getKey();
            if (!shouldExposeToGameLoader(name, patchRoots, packagePrefixes)) {
                continue;
            }

            try {
                targetLoader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                toInject.put(name, entry.getValue());
            }
        }

        if (toInject.isEmpty()) {
            return;
        }

        Map<String, Class<?>> injected = new ClassInjector.UsingUnsafe(targetLoader).injectRaw(toInject);
        Logger.debug("Exposed " + injected.size() + " patch classes to " + targetLoader);
    }

    private static Set<String> packagesForPatches(List<String> patches) {
        Set<String> out = new LinkedHashSet<>();
        for (String patch : patches) {
            String pkg = packagePrefix(patch);
            if (!pkg.isEmpty()) {
                out.add(pkg);
            }
        }

        return out;
    }

    private static String packagePrefix(String className) {
        int dollar = className.indexOf('$');
        String base = dollar >= 0 ? className.substring(0, dollar) : className;
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(0, dot);
    }

    private static boolean shouldExposeToGameLoader(String className, Set<String> patchRoots, Set<String> packagePrefixes) {
        if (patchRoots.contains(className)) {
            return true;
        }

        for (String root : patchRoots) {
            if (className.startsWith(root + "$")) {
                return true;
            }
        }

        for (String pkg : packagePrefixes) {
            if (className.startsWith(pkg + ".")) {
                return true;
            }
        }

        return false;
    }

    /** Define {@code patchClass} in {@code targetLoader} when it lives on a mod {@link ClassLoader}. MethodDelegation requires this. */
    static Class<?> resolveInClassLoader(Class<?> patchClass, ClassLoader targetLoader) {
        if (targetLoader == null || patchClass.getClassLoader() == targetLoader) {
            return patchClass;
        }

        String name = patchClass.getName();
        try {
            return targetLoader.loadClass(name);
        } catch (ClassNotFoundException ignored) {
        }

        byte[] bytes = readClassBytes(patchClass);
        if (bytes == null) {
            Logger.error("Cannot read class bytes for patch class " + name);
            return patchClass;
        }

        if (!ClassInjector.UsingUnsafe.isAvailable()) {
            Logger.error("ClassInjector.UsingUnsafe not available; cannot expose patch class " + name + " to " + targetLoader);
            return patchClass;
        }

        Map<String, Class<?>> injected = new ClassInjector.UsingUnsafe(targetLoader).injectRaw(Map.of(name, bytes));
        Class<?> exposed = injected.get(name);
        if (exposed == null) {
            Logger.error("Failed to expose patch class " + name + " to " + targetLoader);
            return patchClass;
        }

        Logger.debug("Exposed patch class " + name + " to " + targetLoader);
        return exposed;
    }

    private static byte[] readClassBytes(Class<?> cls) {
        String resourceName = cls.getName().replace('.', '/') + ".class";
        ClassLoader loader = cls.getClassLoader();
        if (loader == null) {
            return null;
        }

        try (InputStream in = loader.getResourceAsStream(resourceName)) {
            if (in == null) {
                return null;
            }

            return in.readAllBytes();
        } catch (IOException e) {
            Logger.error("Failed to read class bytes for " + cls.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
