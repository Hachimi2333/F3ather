package top.hachimi2333.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.ClientHooks;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hachimi2333.F3ather;
import top.hachimi2333.config.F3atherScreen;
import top.hachimi2333.overlay.OverlaySettings.Mode;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean handledDebugKey;

    @Unique private boolean f3ather$f3Down;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void f3ather$chord(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        if (window != minecraft.getWindow().getWindow()) return;
        if (key == GLFW.GLFW_KEY_F3) {
            f3ather$f3Down = action != GLFW.GLFW_RELEASE;
            // A screen bypasses vanilla's F3 release handling.
            if (!f3ather$f3Down && minecraft.screen instanceof F3atherScreen) handledDebugKey = false;
            return;
        }
        if (!f3ather$f3Down || action == GLFW.GLFW_RELEASE || minecraft.level == null
                || minecraft.screen != null || minecraft.getOverlay() != null) return;
        var settings = F3ather.settings();
        String shortcut = settings.shortcut();
        if (!shortcut.isEmpty() && key == shortcut.charAt(0)) {
            if (action == GLFW.GLFW_PRESS) minecraft.setScreen(new F3atherScreen(null));
        } else if (settings.mode() == Mode.F3ATHER && minecraft.getDebugOverlay().showDebugScreen()
                && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            F3ather.HUD.advancePage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
        } else {
            return;
        }
        handledDebugKey = true;
        KeyMapping.set(InputConstants.getKey(key, scan), false);
        ClientHooks.onKeyInput(key, scan, action, modifiers);
        callback.cancel();
    }
}
