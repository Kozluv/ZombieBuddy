package me.zed_0xff.zombie_buddy.testpatches;

// verifies: non-optional unresolvable @MethodHandle drops the entire patch class
// @Patch(className = "testjar.MemberHandleTarget", methodName = "doSkipPatch")
// public class PatchMemberHandleSkip {
//     @Patch.MethodHandle(name = "noSuchMethod", className = "testjar.MemberHandleHelper", returnType = void.class, paramTypes = {})
//     static MethodHandle missing;
//
//     @Patch.OnEnter
//     public static void enter() {
//         MemberHandleHelper.skipPatchRan = true;
//     }
// }
