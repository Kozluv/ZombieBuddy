package me.zed_0xff.zombie_buddy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class Reflect_fastcall_Test {
    static final class Target {
        static final AtomicInteger valueCalls = new AtomicInteger();

        static int value() {
            valueCalls.incrementAndGet();
            return 42;
        }

        static String name() {
            valueCalls.incrementAndGet();
            return "ok";
        }

        static int boom() {
            throw new IllegalStateException("boom");
        }
    }

    private static final MethodHandle VALUE_HANDLE;
    private static final MethodHandle NAME_HANDLE;
    private static final MethodHandle BOOM_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup.findStatic(Target.class, "value", MethodType.methodType(int.class));
            NAME_HANDLE  = lookup.findStatic(Target.class, "name", MethodType.methodType(String.class));
            BOOM_HANDLE  = lookup.findStatic(Target.class, "boom", MethodType.methodType(int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle valueHandle() {
        return VALUE_HANDLE;
    }

    private static MethodHandle nameHandle() {
        return NAME_HANDLE;
    }

    @Test
    void fastcall_returnsResolvedHandle() throws Throwable {
        AtomicInteger resolveCalls = new AtomicInteger();
        Target.valueCalls.set(0);

        MethodHandle mh = Reflect.fastcall(() -> {
            resolveCalls.incrementAndGet();
            return valueHandle();
        });

        assertEquals(42, (int) mh.invokeExact());
        assertEquals(1, resolveCalls.get());
        assertEquals(1, Target.valueCalls.get());
    }

    @Test
    void fastcall_retriesWhenResolveReturnsNull() throws Throwable {
        AtomicInteger resolveCalls = new AtomicInteger();
        Target.valueCalls.set(0);

        Reflect.MHResolver resolver = () -> {
            resolveCalls.incrementAndGet();
            if (resolveCalls.get() < 2) return null;

            return valueHandle();
        };

        assertNull(Reflect.fastcall(resolver));
        assertEquals(1, resolveCalls.get());
        assertEquals(0, Target.valueCalls.get());

        MethodHandle mh = Reflect.fastcall(resolver);
        assertEquals(42, (int) mh.invokeExact());
        assertEquals(2, resolveCalls.get());
        assertEquals(1, Target.valueCalls.get());
    }

    @Test
    void fastcall_pinsSuccessfulHandle() throws Throwable {
        AtomicInteger resolveCalls = new AtomicInteger();
        Target.valueCalls.set(0);

        Reflect.MHResolver resolver = () -> {
            resolveCalls.incrementAndGet();
            return valueHandle();
        };

        MethodHandle mh = Reflect.fastcall(resolver);
        assertEquals(42, (int) mh.invokeExact());
        assertEquals(42, (int) Reflect.fastcall(resolver).invokeExact());
        assertEquals(1, resolveCalls.get());
        assertEquals(2, Target.valueCalls.get());
    }

    @Test
    void fastcall_returnsNullWhenResolveFails() {
        assertNull(Reflect.fastcall(() -> null));
    }

    @Test
    void fastcall_throwingHandlePropagates() {
        MethodHandle mh = Reflect.fastcall(() -> BOOM_HANDLE);

        assertThrows(IllegalStateException.class, () -> { invokeInt(mh); });
    }

    @Test
    void fastcall_cachesByResolverIdentity() {
        Reflect.MHResolver resolver = () -> valueHandle();

        assertSame(Reflect.fastcall(resolver), Reflect.fastcall(resolver));
    }

    @Test
    void fastcall_stringInvokeExact() throws Throwable {
        Target.valueCalls.set(0);

        MethodHandle mh = Reflect.fastcall(() -> nameHandle());

        assertEquals("ok", (String) mh.invokeExact());
        assertEquals(1, Target.valueCalls.get());
    }

    @Test
    void fastcall_retriesAfterResolverThrows() throws Throwable {
        AtomicInteger resolveCalls = new AtomicInteger();
        Target.valueCalls.set(0);

        Reflect.MHResolver resolver = () -> {
            if (resolveCalls.incrementAndGet() == 1) throw new IllegalStateException("not yet");

            return valueHandle();
        };

        assertThrows(IllegalStateException.class, () -> Reflect.fastcall(resolver));
        assertEquals(42, (int) Reflect.fastcall(resolver).invokeExact());
        assertTrue(resolveCalls.get() >= 2);
        assertEquals(1, Target.valueCalls.get());
    }

    private static int invokeInt(MethodHandle mh) throws Throwable {
        return (int) mh.invokeExact();
    }
}
