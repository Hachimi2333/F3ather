package top.hachimi2333.f3ather_fixture.mixin;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.hachimi2333.f3ather_fixture.FixtureClient;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = DebugScreenOverlay.class, priority = 900)
abstract class DebugOverlayProbe {
    @Redirect(method = "getSystemInformation", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;",
            remap = false), require = 0)
    private ArrayList<String> f3atherFixture$insertSystemRow(Object[] values) {
        var rows = Lists.newArrayList((String[]) values);
        rows.add(4, "Before event (system): ready");
        return rows;
    }

    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> f3atherFixture$insertGameRow(List<String> rows) {
        rows.add(2, "Before event (game): ready");
        return rows;
    }

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
