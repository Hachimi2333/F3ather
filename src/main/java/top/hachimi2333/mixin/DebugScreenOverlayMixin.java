package top.hachimi2333.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import top.hachimi2333.config.TabEditorScreen;
import top.hachimi2333.overlay.AddonEntries;
import top.hachimi2333.overlay.HudLayout;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean renderDebug;
    @Shadow private boolean renderProfilerChart;
    @Shadow private boolean renderFpsCharts;
    @Shadow private boolean renderNetworkCharts;
    @Shadow private HitResult block;
    @Shadow private HitResult liquid;

    @Unique private OverlaySettings f3ather$settings;
    @Unique private boolean f3ather$refresh;
    @Unique private boolean f3ather$preview;
    @Unique private boolean f3ather$originalRows;
    @Unique private boolean f3ather$editedFont;
    @Unique private HudLayout.Position f3ather$position;
    @Unique private AddonEntries.Column f3ather$left;
    @Unique private AddonEntries.Column f3ather$right;

    @Inject(method = "toggleOverlay", at = @At("TAIL"))
    private void f3ather$toggle(CallbackInfo callback) {
        F3ather.HUD.toggle(renderDebug && !minecraft.options.hideGui, F3ather.settings());
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void f3ather$reset(CallbackInfo callback) {
        F3ather.HUD.resetWorld();
    }

    @WrapMethod(method = "render")
    private void f3ather$render(GuiGraphics graphics, Operation<Void> original) {
        var settings = F3ather.settings();
        boolean preview = minecraft.screen instanceof TabEditorScreen;
        if (settings.compatibility() && !preview) {
            F3ather.HUD.invalidate();
            F3ather.HUD.resetMotion();
            original.call(graphics);
            return;
        }
        if (minecraft.getCameraEntity() == null || minecraft.getConnection() == null) return;
        boolean editorSample = F3ather.HUD.samplingEditor();
        boolean fps = renderFpsCharts;
        boolean network = renderNetworkCharts;
        if (editorSample) {
            renderFpsCharts = false;
            renderNetworkCharts = false;
        }
        try {
            int charts = (renderProfilerChart && !editorSample ? 1 : 0)
                    | (renderFpsCharts ? 2 : 0) | (renderNetworkCharts ? 4 : 0);
            f3ather$preview = preview;
            f3ather$settings = settings;
            f3ather$refresh = F3ather.HUD.begin(minecraft, settings, charts);
            original.call(graphics);
        } finally {
            f3ather$settings = null;
            f3ather$left = null;
            f3ather$right = null;
            f3ather$position = null;
            f3ather$preview = false;
            f3ather$originalRows = false;
            if (editorSample) {
                renderFpsCharts = fps;
                renderNetworkCharts = network;
            }
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult f3ather$pick(Entity camera, double range, float partialTick, boolean fluids,
                                  Operation<HitResult> original) {
        if (f3ather$settings == null || f3ather$refresh) return original.call(camera, range, partialTick, fluids);
        return fluids ? liquid : block;
    }

    @WrapMethod(method = "collectGameInformationText", remap = false)
    private List<String> f3ather$gameText(Operation<List<String>> original) {
        if (f3ather$settings == null) return original.call();
        f3ather$left = F3ather.HUD.column(minecraft, true, f3ather$refresh ? original.call() : null);
        return f3ather$left;
    }

    @WrapMethod(method = "collectSystemInformationText", remap = false)
    private List<String> f3ather$systemText(Operation<List<String>> original) {
        if (f3ather$settings == null) return original.call();
        f3ather$right = F3ather.HUD.column(minecraft, false, f3ather$refresh ? original.call() : null);
        return f3ather$right;
    }

    @WrapMethod(method = "renderLines")
    private void f3ather$draw(GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        if (f3ather$settings == null || f3ather$left == null || f3ather$right == null
                || lines != (left ? f3ather$left : f3ather$right)) {
            original.call(graphics, lines, left);
            return;
        }
        if (left) {
            F3ather.HUD.accept(f3ather$left, f3ather$right, f3ather$settings);
            f3ather$originalRows = BuiltIn.VANILLA.id().equals(F3ather.HUD.activeTab());
        }
        if (f3ather$preview) return;
        if (left) f3ather$position = F3ather.HUD.position(f3ather$settings, graphics.guiWidth());
        minecraft.getProfiler().push("f3ather_text");
        try {
            if (f3ather$originalRows) {
                if (left) F3ather.HUD.presentVanilla(graphics, f3ather$settings, f3ather$position, f3ather$left, f3ather$right);
                f3ather$drawVanillaColumn(graphics, lines, left, original);
            } else if (left) {
                F3ather.HUD.drawSelected(graphics, f3ather$settings, f3ather$position);
            }
        } finally {
            minecraft.getProfiler().pop();
        }
    }

    @Unique
    private void f3ather$drawVanillaColumn(GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        int width = graphics.guiWidth();
        int offset = f3ather$position.columnX(left);
        if (Math.abs(offset) >= width) return;
        if (offset != 0) {
            graphics.enableScissor(Math.max(0, offset), 0, Math.min(width, width + offset), graphics.guiHeight());
        }
        graphics.pose().pushPose();
        graphics.pose().translate(offset, f3ather$position.headerHeight(), 0);
        f3ather$editedFont = true;
        try {
            original.call(graphics, lines, left);
        } finally {
            f3ather$editedFont = false;
            graphics.pose().popPose();
            if (offset != 0) graphics.disableScissor();
        }
    }

    @WrapOperation(method = "renderLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int f3ather$width(Font font, String value, Operation<Integer> original) {
        return f3ather$editedFont ? F3ather.HUD.text().width(font, value, 0xe0e0e0) : original.call(font, value);
    }

    @WrapOperation(method = "renderLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int f3ather$text(GuiGraphics graphics, Font font, String value, int x, int y, int color, boolean shadow,
                             Operation<Integer> original) {
        if (f3ather$editedFont && !shadow) return F3ather.HUD.text().draw(graphics, font, value, x, y, color);
        return original.call(graphics, font, value, x, y, color, shadow);
    }
}
