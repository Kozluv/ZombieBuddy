package me.zed_0xff.zombie_buddy.testpatches;

import me.zed_0xff.zombie_buddy.Reflect;
import me.zed_0xff.zombie_buddy.annotations.Patch;

/**
 * Test patch using MethodDelegation to replace a constructor with parameters.
 */
@SuppressWarnings("removal")
@Patch(
    className = "testjar.ConstructorDelegationTargetA*",
    methodName = "<init>",
    isAdvice = false
)
public class PatchConstructorDelegationA {

    @Patch.RuntimeType
    public static void constructor(
            @Patch.This Object self,
            @Patch.Argument(0) int value,
            @Patch.Argument(1) String name) throws Throwable
    {
        System.out.println("[ZB TEST] PatchConstructorDelegation.constructor called with value=" + value + ", name=" + name);

        Reflect r = Reflect.on(self);
        r.set("value", value * 10);
        r.set("name", name + " patched");
        r.set("patchIntercepted", Boolean.TRUE);
        System.out.println("[ZB TEST] Setting patchIntercepted=true");
        Object patchIntercepted = r.field("patchIntercepted").get(Boolean.FALSE);
        System.out.println("[ZB TEST] patchIntercepted is now: " + patchIntercepted);
    }
}
