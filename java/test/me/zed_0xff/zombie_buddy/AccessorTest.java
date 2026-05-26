package me.zed_0xff.zombie_buddy;

/**
 * Tests for {@link Accessor} tryGet, trySet, findExactMethod, hasPublicMethod, and call.
 */
public class AccessorTest {

    // @SuppressWarnings("unused")
    // public static class Target {
    //     public static final Target INSTANCE = new Target();
    //     public static final int publicStaticFinalInt = 123;
    //     public int publicInt = 42;
    //     private int privateInt = -7;
    //     public Integer publicInteger = 100;
    //     private Integer privateInteger = 200;
    //     public String publicString = "hello";
    //     private String privateString = "world";
    //     public Object publicNull = null;
    //
    //     public String getPublicString() {
    //         return publicString;
    //     }
    //
    //     private int privateGetInt() {
    //         return privateInt;
    //     }
    //
    //     public String echo(String value) {
    //         return "echo:" + value;
    //     }
    //
    //     public Target child() {
    //         Target child = new Target();
    //         child.publicString = "child";
    //         return child;
    //     }
    // }
    //
    // @SuppressWarnings("unused")
    // public static class TargetWithGetInstance {
    //     static final TargetWithGetInstance instance = new TargetWithGetInstance("field");
    //     private static final TargetWithGetInstance METHOD_INSTANCE = new TargetWithGetInstance("method");
    //     final String source;
    //
    //     TargetWithGetInstance(String source) {
    //         this.source = source;
    //     }
    //
    //     public static TargetWithGetInstance getInstance() {
    //         return METHOD_INSTANCE;
    //     }
    // }
    //
    // @SuppressWarnings("unused")
    // public static class TargetWithInstanceField {
    //     static final TargetWithInstanceField instance = new TargetWithInstanceField("field");
    //     final String source;
    //
    //     TargetWithInstanceField(String source) {
    //         this.source = source;
    //     }
    // }
    //
    // @SuppressWarnings("unused")
    // public static class TargetWithoutInstance {
    // }
    //
    // @SuppressWarnings("unused")
    // public static class TargetSub extends Target {
    //     private int childOnly = 999;
    // }

    // --- Reflect ---

    // @Test
    // void query_staticField_call_as_returnsTypedOptional() {
    //     assertEquals("hello", Reflect.on(Target.class)
    //         .staticField("INSTANCE")
    //         .call("getPublicString")
    //         .as(String.class)
    //         .orElseThrow());
    // }
    //
    // @Test
    // void query_className_staticField_call_as_returnsTypedOptional() {
    //     assertEquals("hello", Reflect.on(Target.class.getName())
    //         .staticField("INSTANCE")
    //         .call("getPublicString")
    //         .as(String.class)
    //         .orElseThrow());
    // }
    //
    // @Test
    // void query_field_call_supportsChainingThroughObjects() {
    //     assertEquals("child", Reflect.on(new Target())
    //         .call("child")
    //         .field("publicString")
    //         .as(String.class)
    //         .orElseThrow());
    // }
    //
    // @Test
    // void query_call_supportsArguments() {
    //     assertEquals("echo:value", Reflect.on(new Target())
    //         .call("echo", "value")
    //         .as(String.class)
    //         .orElseThrow());
    // }

    // @Test
    // void query_getInstance_prefersStaticGetInstanceMethod() {
    //     TargetWithGetInstance result = Reflect.on(TargetWithGetInstance.class)
    //         .getInstance()
    //         .as(TargetWithGetInstance.class)
    //         .orElseThrow();
    //
    //     assertEquals("method", result.source);
    // }
    //
    // @Test
    // void query_getInstance_fallsBackToStaticInstanceField() {
    //     TargetWithInstanceField result = Reflect.on(TargetWithInstanceField.class)
    //         .getInstance()
    //         .as(TargetWithInstanceField.class)
    //         .orElseThrow();
    //
    //     assertEquals("field", result.source);
    // }

    // @Test
    // void query_getInstance_returnsEmptyWhenNoMethodOrField() {
    //     assertFalse(Reflect.on(TargetWithoutInstance.class)
    //         .getInstance()
    //         .asObject()
    //         .isPresent());
    // }

    // @Test
    // void query_missingClass_returnsEmpty() {
    //     assertFalse(Reflect.on("no.such.Class")
    //         .staticField("INSTANCE")
    //         .call("getPublicString")
    //         .as(String.class)
    //         .isPresent());
    // }
    //
    // @Test
    // void query_missingField_returnsEmpty() {
    //     assertFalse(Reflect.on(Target.class)
    //         .staticField("missing")
    //         .call("getPublicString")
    //         .as(String.class)
    //         .isPresent());
    // }
    //
    // @Test
    // void query_missingMethod_returnsEmpty() {
    //     assertFalse(Reflect.on(Target.class)
    //         .staticField("INSTANCE")
    //         .call("missing")
    //         .as(String.class)
    //         .isPresent());
    // }
    //
    // @Test
    // void query_asWrongType_returnsEmpty() {
    //     assertFalse(Reflect.on(Target.class)
    //         .staticField("INSTANCE")
    //         .call("getPublicString")
    //         .as(Integer.class)
    //         .isPresent());
    // }

    // @Test
    // void query_orElse_returnsDefaultWhenEmpty() {
    //     assertEquals("default", Reflect.on(Target.class)
    //         .staticField("missing")
    //         .get("default"));
    // }

    // @Test
    // void query_isPresentAndAsObjectReflectCurrentValue() {
    //     Reflect query = Reflect.on(Target.class).staticField("INSTANCE");
    //     assertTrue(query.isPresent());
    //     assertSame(Target.INSTANCE, query.asObject().orElseThrow());
    // }
}
