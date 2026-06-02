package me.zed_0xff.zombie_buddy.testpatches;

import me.zed_0xff.zombie_buddy.annotations.Patch;

@Patch(className = "testjar.FieldValueTarget", methodName = "increment")
public class PatchRWField {
    @Patch.OnEnter
    public static void enter(@Patch.Field int counter) {
        counter++;
    }
}
