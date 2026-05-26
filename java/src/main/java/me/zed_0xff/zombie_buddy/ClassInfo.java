package me.zed_0xff.zombie_buddy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ClassInfo {
    static final Object MISS = new Object();

    final MethodHandles.Lookup              lookup;
    final ConcurrentHashMap<String, Object> varCache    = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Object> methodCache = new ConcurrentHashMap<>();
    // mutable
    private List<Method>                    methods     = null;
    private Map<String, Field>              fields      = null;

    ClassInfo(Class<?> cls) {
        MethodHandles.Lookup l;
        try {
            l = cls == null ? null : MethodHandles.privateLookupIn(cls, MethodHandles.lookup());
        } catch (Throwable t) {
            Logger.once.error("failed to create lookup for", cls, ":", t);
            l = null;
        }
        lookup = l;
    }

    // expensive operation, should run once per class and cache the result
    // should not return null
    private static final List<Method> fetchMethods(Class<?> cls) {
        List<Method> out = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (/*m.isSynthetic() ||*/ m.isBridge() || m.getDeclaringClass() == Object.class) continue;
                out.add(m);
            }
        }
        return out;
    }

    // expensive operation, should run once per class and cache the result
    // should not return null; one entry per name (most-derived wins)
    private static final Map<String, Field> fetchFields(Class<?> cls) {
        Map<String, Field> out = new LinkedHashMap<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                out.putIfAbsent(f.getName(), f);
            }
        }
        return out;
    }

    public List<Method> methods() {
        if (methods == null) methods = fetchMethods(lookup.lookupClass());
        return methods;
    }

    public Map<String, Field> fields() {
        if (fields == null) fields = fetchFields(lookup.lookupClass());
        return fields;
    }

    public VarHandle getVarHandle(String fieldName) {
        Object vho = varCache.get(fieldName);
        if (vho instanceof VarHandle vh) return vh;
        if (vho == MISS) return null;

        Field f = fields().get(fieldName);
        if (f == null) {
            varCache.put(fieldName, MISS);
            return null;
        }
        return getVarHandle(f);
    }

    public VarHandle getVarHandle(Field f) {
        Object vho = varCache.get(f.getName());
        if (vho instanceof VarHandle vh) return vh;
        if (vho == MISS) return null;

        try {
            VarHandle vh = lookup.unreflectVarHandle(f);
            varCache.put(f.getName(), vh == null ? MISS : vh);
            return vh;
        } catch (Throwable t) {
            Logger.once.warn("failed to unreflectVarHandle for", f, "of", lookup.lookupClass(), ":", t);
            varCache.put(f.getName(), MISS);
            return null;
        }
    }
}
