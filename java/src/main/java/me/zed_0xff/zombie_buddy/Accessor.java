package me.zed_0xff.zombie_buddy;

//@Deprecated(since = "2026-05-01")
// public final class Accessor {
//
//     // Class-name -> Class<?> lives separately: we need the Class object before we can create a ClassInfo.
//     private static final ConcurrentHashMap<String, Optional<Class<?>>> classNameCache = new ConcurrentHashMap<>();
//
//     // Per-class cache. On second access the class is fully prewarmed (all fields and declared methods scanned).
//     private static final ConcurrentHashMap<Class<?>, ClassInfo> cache = new ConcurrentHashMap<>();
//
//     private static final class ClassInfo {
//         // final AtomicInteger accessCount = new AtomicInteger(0);
//         // Set to true only after prewarm() fully completes (volatile for happens-before).
//         // volatile boolean prewarmed = false;
//         final ConcurrentHashMap<String, Optional<Field>>    fields        = new ConcurrentHashMap<>();
//         final ConcurrentHashMap<String, List<Method>>       methodsByName = new ConcurrentHashMap<>();
//         // key: methodName + '\0' + param1Name + '\0' + param2Name ...
//         final ConcurrentHashMap<String, Optional<Method>>   exactMethods  = new ConcurrentHashMap<>();
//         // populated lazily before prewarm; replaced by publicMethodNames after
//         final ConcurrentHashMap<String, Boolean>            publicMethods     = new ConcurrentHashMap<>();
//         volatile Set<String>  publicMethodNames = null;
//         volatile List<Method> publicMethodList  = null;
//     }
//
//     private Accessor() {}
//
//     private static ClassInfo getClassInfo(Class<?> cls) {
//         ClassInfo info = cache.computeIfAbsent(cls, k -> new ClassInfo());
//         // incrementAndGet is atomic: exactly one thread observes count==2, so no CAS needed.
//         // if (info.accessCount.incrementAndGet() == 2) {
//         //     prewarm(cls, info);
//         // }
//         return info;
//     }
//
//     // private static void prewarm(Class<?> cls, ClassInfo info) {
//     //     for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
//     //         for (Field f : c.getDeclaredFields()) {
//     //             info.fields.putIfAbsent(f.getName(), Optional.of(f));
//     //         }
//     //     }
//     //     Map<String, List<Method>> byName = new HashMap<>();
//     //     for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
//     //         for (Method m : c.getDeclaredMethods()) {
//     //             byName.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
//     //             info.exactMethods.putIfAbsent(exactMethodKey(m.getName(), m.getParameterTypes()), Optional.of(m));
//     //         }
//     //     }
//     //     for (Map.Entry<String, List<Method>> e : byName.entrySet()) {
//     //         info.methodsByName.putIfAbsent(e.getKey(), Collections.unmodifiableList(e.getValue()));
//     //     }
//     //     Set<String> names = new HashSet<>();
//     //     List<Method> pubList = new ArrayList<>();
//     //     for (Method m : cls.getMethods()) {
//     //         names.add(m.getName());
//     //         pubList.add(m);
//     //     }
//     //     info.publicMethodNames = Collections.unmodifiableSet(names);
//     //     info.publicMethodList  = Collections.unmodifiableList(pubList);
//     //     info.prewarmed = true;
//     // }
//
//     // private static String exactMethodKey(String methodName, Class<?>[] parameterTypes) {
//     //     StringBuilder sb = new StringBuilder(methodName).append('\0');
//     //     if (parameterTypes != null) {
//     //         for (int i = 0; i < parameterTypes.length; i++) {
//     //             if (i > 0) sb.append('\0');
//     //             sb.append(parameterTypes[i].getName());
//     //         }
//     //     }
//     //     return sb.toString();
//     // }
//
//     public static void clearCaches() {
//         Logger.debug("Clearing Accessor caches");
//         classNameCache.clear();
//         cache.clear();
//     }
//
//     /** Returns the first loadable class from the given names, or null. Never throws. */
//     public static Class<?> findClass(String... classNames) {
//         if (classNames == null || classNames.length == 0) {
//             return null;
//         }
//         for (String className : classNames) {
//             if (!Utils.isBlank(className)) {
//                 String normalized = Utils.toCanonicalName(className);
//                 Class<?> cls = classNameCache
//                     .computeIfAbsent(normalized, k -> Optional.ofNullable(findClassUncached(k)))
//                     .orElse(null);
//                 if (cls != null) {
//                     return cls;
//                 }
//             }
//         }
//         return null;
//     }
//
//     private static Class<?> findClassUncached(String className) {
//         try {
//             return Class.forName(className);
//         } catch (ClassNotFoundException | LinkageError e) {
//             return null;
//         }
//     }
// }
