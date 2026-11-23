package top.hachimi2333.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.OverlayCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean renderProfilerChart;
    @Shadow private boolean renderFpsCharts;
    @Shadow private boolean renderNetworkCharts;

    @Unique private final OverlayCache f3ather$cache = new OverlayCache();
    @Unique private OverlayCache.Frame f3ather$frame;

    @Inject(method = {"toggleOverlay", "reset"}, at = @At("HEAD"))
    private void f3ather$invalidate(CallbackInfo callback) {
        f3ather$cache.invalidate();
    }

    @WrapMethod(method = "render")
    private void f3ather$render(GuiGraphics graphics, Operation<Void> original) {
        int charts = (renderProfilerChart ? 1 : 0) | (renderFpsCharts ? 2 : 0) | (renderNetworkCharts ? 4 : 0);
        var context = new OverlayCache.Context(minecraft.level, minecraft.getCameraEntity(),
                minecraft.showOnlyReducedInfo(), charts, F3ather.settings());
        f3ather$frame = f3ather$cache.begin(System.nanoTime(), context);
        try {
            original.call(graphics);
        } finally {
            f3ather$frame = null;
        }
    }

    @WrapMethod(method = "collectGameInformationText", remap = false)
    private List<String> f3ather$gameText(Operation<List<String>> original) {
        if (f3ather$frame == null) {
            return original.call();
        }
        return f3ather$frame.collect(true, original::call, () -> null);
    }

    @WrapMethod(method = "collectSystemInformationText", remap = false)
    private List<String> f3ather$systemText(Operation<List<String>> original) {
        if (f3ather$frame == null) {
            return original.call();
        }
        return f3ather$frame.collect(false, original::call, this::f3ather$demoLine);
    }

    @WrapMethod(method = "renderLines")
    private void f3ather$draw(GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        if (f3ather$frame != null) {
            lines = f3ather$frame.forDrawing(lines, left, F3ather.ADDONS);
        }
        original.call(graphics, lines, left);
    }

    @Unique
    private String f3ather$demoLine() {
        if (!minecraft.isDemo() || minecraft.level == null) {
            return null;
        }
        long remaining = 120500L - minecraft.level.getGameTime();
        if (remaining <= 0) {
            return Component.translatable("demo.demoExpired").getString();
        }
        return Component.translatable("demo.remainingTime",
                StringUtil.formatTickDuration((int) remaining, minecraft.level.tickRateManager().tickrate())).getString();
    }
}
