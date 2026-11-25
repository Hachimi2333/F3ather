package top.hachimi2333.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hachimi2333.F3ather;
import top.hachimi2333.config.TabEditorScreen;
import top.hachimi2333.overlay.AddonEntries;
import top.hachimi2333.overlay.HudLayout;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.RawDebugRows;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
    @Unique private RawDebugRows f3ather$rawRows;

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
        f3ather$left = f3ather$sampleColumn(true, original);
        return f3ather$left;
    }

    @WrapMethod(method = "collectSystemInformationText", remap = false)
    private List<String> f3ather$systemText(Operation<List<String>> original) {
        if (f3ather$settings == null) return original.call();
        f3ather$right = f3ather$sampleColumn(false, original);
        return f3ather$right;
    }

    @Unique
    private AddonEntries.Column f3ather$sampleColumn(boolean left, Operation<List<String>> original) {
        if (!f3ather$refresh) return F3ather.HUD.column(left, null);
        var trace = new RawDebugRows();
        f3ather$rawRows = trace;
        try {
            var actual = original.call();
            var rows = trace.resolve(actual, left, minecraft.getSingleplayerServer() != null, minecraft.isDemo(),
                    ModList.get().isLoaded("sodium"));
            return F3ather.HUD.column(left, rows);
        } finally {
            f3ather$rawRows = null;
        }
    }

    // Keep the constructor's arguments intact. A redirect such as Sodium's can change the returned list.
    @ModifyArg(method = {"getGameInformation", "getSystemInformation"}, at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;",
            remap = false), index = 0, require = 0)
    private Object[] f3ather$initialRows(Object[] values) {
        if (f3ather$rawRows != null) f3ather$rawRows.initial(values);
        return values;
    }

    @ModifyArg(method = {"getGameInformation", "getSystemInformation"}, at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), index = 0, require = 0)
    private Object f3ather$stockRow(Object value) {
        if (f3ather$rawRows != null) f3ather$rawRows.append(value);
        return value;
    }

    @ModifyArg(method = "collectGameInformationText", remap = false, at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), index = 0, require = 0)
    private Object f3ather$neoForgeHint(Object value) {
        return f3ather$stockRow(value);
    }

    @ModifyArg(method = "getSystemInformation", at = @At(value = "INVOKE",
            target = "Ljava/util/List;add(ILjava/lang/Object;)V", remap = false), index = 1, require = 0)
    private Object f3ather$demoRow(Object value) {
        if (f3ather$rawRows != null) f3ather$rawRows.prepend(value);
        return value;
    }

    @WrapOperation(method = "getGameInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;addDebugScreenInfo(Ljava/util/List;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/core/BlockPos;)V"),
            require = 0)
    private void f3ather$noiseRows(ChunkGenerator generator, List<String> rows, RandomState state, BlockPos pos,
                                   Operation<Void> original) {
        int start = f3ather$rawRows == null ? 0 : rows.size();
        original.call(generator, rows, state, pos);
        if (f3ather$rawRows != null) f3ather$rawRows.appendWithPrefix(rows, start, "NoiseRouter T: ");
    }

    @WrapOperation(method = "getGameInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/BiomeSource;addDebugInfo(Ljava/util/List;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),
            require = 0)
    private void f3ather$biomeRows(BiomeSource biomes, List<String> rows, BlockPos pos, Climate.Sampler sampler,
                                   Operation<Void> original) {
        int start = f3ather$rawRows == null ? 0 : rows.size();
        original.call(biomes, rows, pos, sampler);
        if (f3ather$rawRows != null) f3ather$rawRows.appendWithPrefix(rows, start, "Biome builder PV: ");
    }

    @WrapOperation(method = "getSystemInformation", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V", remap = false), require = 0)
    private void f3ather$targetTags(Stream<?> tags, Consumer<?> consumer, Operation<Void> original,
                                    @Local(ordinal = 0) List<String> rows) {
        int start = f3ather$rawRows == null ? 0 : rows.size();
        original.call(tags, consumer);
        if (f3ather$rawRows != null) f3ather$rawRows.appendWithPrefix(rows, start, "#");
    }

    @WrapMethod(method = "renderLines")
    private void f3ather$draw(GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        if (f3ather$settings == null || f3ather$left == null || f3ather$right == null
                || lines != (left ? f3ather$left : f3ather$right)) {
            original.call(graphics, lines, left);
            return;
        }
        if (left) {
            F3ather.HUD.accept(f3ather$left, f3ather$right, f3ather$settings, f3ather$preview);
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
