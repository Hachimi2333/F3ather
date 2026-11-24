package top.hachimi2333.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.OverlayHud;
import top.hachimi2333.overlay.OverlaySettings.Mode;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean renderProfilerChart;
    @Shadow private boolean renderFpsCharts;
    @Shadow private boolean renderNetworkCharts;

    @Unique private OverlayHud.Frame f3ather$frame;
    @Unique private boolean f3ather$usingHud;

    @Inject(method = {"toggleOverlay", "reset"}, at = @At("HEAD"))
    private void f3ather$invalidate(CallbackInfo callback) {
        F3ather.HUD.invalidate();
    }

    @WrapMethod(method = "render")
    private void f3ather$render(GuiGraphics graphics, Operation<Void> original) {
        var settings = F3ather.settings();
        if (settings.mode() == Mode.TRADITIONAL || minecraft.getCameraEntity() == null) {
            if (f3ather$usingHud) F3ather.HUD.invalidate();
            f3ather$usingHud = false;
            original.call(graphics);
            return;
        }
        int charts = (renderProfilerChart ? 1 : 0) | (renderFpsCharts ? 2 : 0) | (renderNetworkCharts ? 4 : 0);
        f3ather$usingHud = true;
        f3ather$frame = F3ather.HUD.begin(minecraft, settings, charts);
        try {
            original.call(graphics);
        } finally {
            f3ather$frame = null;
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult f3ather$pick(Entity camera, double range, float partialTick, boolean fluids,
                                  Operation<HitResult> original) {
        return f3ather$frame == null ? original.call(camera, range, partialTick, fluids) : null;
    }

    @WrapMethod(method = "collectGameInformationText", remap = false)
    private List<String> f3ather$gameText(Operation<List<String>> original) {
        return f3ather$frame == null ? original.call() : f3ather$frame.left();
    }

    @WrapMethod(method = "collectSystemInformationText", remap = false)
    private List<String> f3ather$systemText(Operation<List<String>> original) {
        return f3ather$frame == null ? original.call() : f3ather$frame.right();
    }

    @WrapMethod(method = "renderLines")
    private void f3ather$draw(GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        if (f3ather$frame == null) {
            original.call(graphics, lines, left);
        } else if (lines == f3ather$frame.right()) {
            F3ather.HUD.draw(graphics, f3ather$frame);
        }
    }
}
