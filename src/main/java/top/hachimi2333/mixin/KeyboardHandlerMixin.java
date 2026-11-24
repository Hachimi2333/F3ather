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
import top.hachimi2333.config.TabEditorScreen;

import java.util.HashSet;
import java.util.Set;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean handledDebugKey;
    @Unique private boolean f3ather$f3Down;
    @Unique private final Set<Integer> f3ather$ownedKeys = new HashSet<>();

    @Inject(method = "keyPress", at = @At("RETURN"))
    private void f3ather$hideGui(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        if (window == minecraft.getWindow().getWindow() && key == GLFW.GLFW_KEY_F1
                && action == GLFW.GLFW_PRESS && minecraft.options.hideGui) F3ather.HUD.resetMotion();
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void f3ather$keys(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        if (window != minecraft.getWindow().getWindow()) return;
        if (key == GLFW.GLFW_KEY_F3) {
            f3ather$f3Down = action != GLFW.GLFW_RELEASE;
            if (!f3ather$f3Down && (minecraft.screen instanceof F3atherScreen
                    || minecraft.screen instanceof TabEditorScreen)) handledDebugKey = false;
            return;
        }
        if (action == GLFW.GLFW_RELEASE) {
            if (f3ather$ownedKeys.remove(key)) f3ather$consume(key, scan, action, modifiers, callback);
            return;
        }
        if (action == GLFW.GLFW_REPEAT && f3ather$ownedKeys.contains(key)) {
            f3ather$consume(key, scan, action, modifiers, callback);
            return;
        }
        if (action != GLFW.GLFW_PRESS || minecraft.level == null || minecraft.screen != null
                || minecraft.getOverlay() != null) return;

        var settings = F3ather.settings();
        if (f3ather$f3Down && key == GLFW.GLFW_KEY_0) {
            minecraft.setScreen(new F3atherScreen(null));
        } else if (!settings.compatibility() && minecraft.getDebugOverlay().showDebugScreen()) {
            if (key == GLFW.GLFW_KEY_TAB) {
                F3ather.HUD.cycle(settings);
            } else if (f3ather$f3Down && F3ather.HUD.supportsPages()
                    && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
                F3ather.HUD.advancePage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1, settings);
            } else {
                return;
            }
        } else {
            return;
        }

        if (f3ather$f3Down) handledDebugKey = true;
        f3ather$ownedKeys.add(key);
        f3ather$consume(key, scan, action, modifiers, callback);
    }

    @Unique
    private void f3ather$consume(int key, int scan, int action, int modifiers, CallbackInfo callback) {
        KeyMapping.set(InputConstants.getKey(key, scan), false);
        ClientHooks.onKeyInput(key, scan, action, modifiers);
        callback.cancel();
    }
}
