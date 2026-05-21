package me.zed_0xff.zombie_buddy.transformers;

import org.junit.jupiter.api.Test;

import me.zed_0xff.zombie_buddy.transformers.asmtree.Binder;

class BinderTest extends AbstractTest {
    class Target1 {
        private int f1;
        static Object f2;
        private void m1() {}
        static protected boolean m2() { return false; }
    }

    @Test
    void test() throws Exception {
        var cls = Binder.class;
        var ctx = new TestClassContext(Target1.class);
        byte[] bytes = ctx.getBytes();

        Transformer transformer = cls.getDeclaredConstructor().newInstance();
        var result = transformer.transform(bytes, ctx);
        // assertThat(result.modified()).isTrue();
        // assertThat(result.bytes()).isNotNull();
    }
}
