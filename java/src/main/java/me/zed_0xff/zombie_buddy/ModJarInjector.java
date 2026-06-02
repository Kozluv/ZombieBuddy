package me.zed_0xff.zombie_buddy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import me.zed_0xff.zombie_buddy.transformers.TransformedJar;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.ClassInjector;

/** Defines transformed mod jar classes in memory via {@link ByteArrayClassLoader}; no temp file or {@link java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch}. */
final class ModJarInjector {
    private ModJarInjector() {}

    static ClassLoader inject(TransformedJar jar) {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
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

    /** Define all mod jar classes in {@code targetLoader} when they live on another {@link ClassLoader}. MethodDelegation requires this. */
    static void exposeJarInClassLoader(TransformedJar jar, ClassLoader targetLoader) {
        if (jar == null || targetLoader == null || jar.classes().isEmpty()) {
            return;
        }

        if (!ClassInjector.UsingUnsafe.isAvailable()) {
            Logger.error("ClassInjector.UsingUnsafe not available; cannot expose mod jar classes to " + targetLoader);
            return;
        }

        HashMap<String, byte[]> toInject = new HashMap<>();
        for (var entry : jar.classes().entrySet()) {
            String name = entry.getKey();
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
        Logger.debug("Exposed " + injected.size() + " mod classes to " + targetLoader);
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
