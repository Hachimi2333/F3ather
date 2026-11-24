package top.hachimi2333.f3ather_fixture.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.hachimi2333.f3ather_fixture.FixtureClient;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
abstract class DebugOverlayProbe {
    @Inject(method = {"getGameInformation", "getSystemInformation"}, at = @At("HEAD"))
    private void f3atherFixture$collect(CallbackInfoReturnable<List<String>> callback) {
        FixtureClient.collectedColumn();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void f3atherFixture$begin(GuiGraphics graphics, CallbackInfo callback) {
        FixtureClient.startOverlay();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void f3atherFixture$end(GuiGraphics graphics, CallbackInfo callback) {
        FixtureClient.endOverlay();
    }

    @Inject(method = "renderLines", at = @At("HEAD"))
    private void f3atherFixture$beginText(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo callback) {
        FixtureClient.startText();
    }

    @Inject(method = "renderLines", at = @At("RETURN"))
    private void f3atherFixture$endText(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo callback) {
        FixtureClient.endText();
    }
}
