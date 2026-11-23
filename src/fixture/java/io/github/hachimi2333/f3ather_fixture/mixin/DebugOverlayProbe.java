package io.github.hachimi2333.f3ather_fixture.mixin;

import io.github.hachimi2333.f3ather_fixture.FixtureClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
abstract class DebugOverlayProbe {
    @Inject(method = {"getGameInformation", "getSystemInformation"}, at = @At("HEAD"))
    private void f3atherFixture$collect(CallbackInfoReturnable<List<String>> callback) {
        FixtureClient.collectedColumn();
    }

    @Inject(method = "renderLines", at = @At("HEAD"))
    private void f3atherFixture$draw(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo callback) {
        FixtureClient.drawLines(lines);
    }
}
