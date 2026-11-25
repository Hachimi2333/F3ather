package top.hachimi2333.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hachimi2333.F3ather;

@Mixin(FontManager.class)
abstract class FontManagerMixin {
    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void f3ather$invalidateGlyphs(Options options, CallbackInfo callback) {
        F3ather.HUD.text().clear();
    }
}
