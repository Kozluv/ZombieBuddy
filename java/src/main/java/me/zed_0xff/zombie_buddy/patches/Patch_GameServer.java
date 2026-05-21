package me.zed_0xff.zombie_buddy.patches;

import me.zed_0xff.zombie_buddy.Callbacks;
import me.zed_0xff.zombie_buddy.annotations.Patch;

@Patch(className = "zombie.network.GameServer", methodName = "startServer")
public class Patch_GameServer {
    @Patch.OnEnter
    static void enter() {
        Callbacks.onGameInitComplete.run();
    }
}
