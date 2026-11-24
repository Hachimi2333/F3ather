package top.hachimi2333.f3ather_fixture;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Mod(value = "f3ather_fixture", dist = Dist.CLIENT)
public final class FixtureClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKER = "Fixture ";
    private static final long WARMUP_NANOS = 3_000_000_000L;
    private static final long SAMPLE_NANOS = 8_000_000_000L;

    private static final List<Long> FRAME_INTERVALS = new ArrayList<>();
    private static final List<Long> OVERLAY_TIMES = new ArrayList<>();
    private static long warmupAt;
    private static long sampleAt;
    private static long previousFrame;
    private static long start;
    private static long columnsCollected;
    private static int frames;
    private static int counter;
    private static boolean finished;
    private static boolean openingWorld;
    private static boolean traditional;
    private static boolean checked;
    private static boolean rich;
    private static int uiTick;
    private static int closedCounter;

    public FixtureClient() {
        NeoForge.EVENT_BUS.addListener(FixtureClient::addDebugText);
        NeoForge.EVENT_BUS.addListener(FixtureClient::tick);
    }

    private static void addDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!NetworkChecks.GUEST && !NetworkChecks.HOST) updateMeasurement(System.nanoTime());
        List<String> left = event.getLeft();
        List<String> right = event.getRight();
        verifyFresh(left);
        verifyFresh(right);
        NetworkChecks.observe(event, Minecraft.getInstance());
        if (rich && Minecraft.getInstance().showOnlyReducedInfo()) {
            String position = Component.translatable("f3ather.entry.xyz").getString();
            if (left.stream().anyMatch(line -> line != null && line.startsWith(position + " ·"))) {
                throw new IllegalStateException("Restricted coordinates passed to DebugText");
            }
        }

        left.removeIf(line -> line != null && line.startsWith("For help: "));
        if (!left.isEmpty() && left.getFirst() != null) {
            left.set(0, left.getFirst() + " §r" + MARKER + "edited");
        }
        Collections.rotate(right, 1);
        for (int i = 0; i < 48; i++) {
            left.add(MARKER + "left " + i + ": " + (counter + i));
            right.add(MARKER + "right " + i + ": " + (counter - i));
        }
        right.add(MARKER + "status ready");
        left.add("Shared status: ready");
        counter++;
    }

    private static void verifyFresh(List<String> lines) {
        for (String line : lines) {
            if (line != null && line.contains(MARKER)) {
                throw new IllegalStateException("Previous DebugText mutations leaked into a later frame");
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean auto = Boolean.getBoolean("f3ather.fixture.auto");
        boolean f3ather = ModList.get().isLoaded("f3ather");
        if (auto && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) {
            mc.options.onboardingAccessibilityFinished();
            mc.setScreen(new TitleScreen());
        }
        if (auto && mc.screen instanceof TitleScreen && mc.getOverlay() == null && !openingWorld) {
            openingWorld = true;
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.getTutorial().setStep(TutorialSteps.NONE);
            String folder = "f3ather-test";
            if (mc.getLevelSource().levelExists(folder)) {
                mc.createWorldOpenFlows().openWorld(folder, () -> mc.stop());
            } else {
                mc.createWorldOpenFlows().createFreshLevel(folder,
                        new LevelSettings("F3ather Fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(8404642L, true, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            }
        }
        if (mc.level == null || finished) return;
        if (NetworkChecks.HOST) {
            if (mc.screen == null) NetworkChecks.host(mc);
            return;
        }
        if (NetworkChecks.GUEST) {
            NetworkChecks.guest(mc);
            return;
        }
        if (f3ather && auto && !checked && mc.screen == null) {
            checked = true;
            FixtureChecks.prepare();
        }
        if (auto && !rich && mc.screen == null && !mc.getDebugOverlay().showDebugScreen()) {
            mc.getDebugOverlay().toggleOverlay();
        }
        if (sampleAt == 0 || System.nanoTime() - sampleAt < SAMPLE_NANOS) return;
        if (!rich) {
            finish(f3ather && !traditional ? "f3ather" : "traditional");
            if (f3ather && auto && !traditional) {
                traditional = true;
                FixtureChecks.traditional();
                reset();
                return;
            }
            if (f3ather && auto && Boolean.getBoolean("f3ather.fixture.visual")) {
                rich = true;
                FixtureChecks.enableAll();
                LOGGER.info("F3ATHER_VISUAL attached");
                return;
            }
            finished = true;
            if (f3ather && auto) FixtureChecks.restore();
            if (auto) mc.stop();
            return;
        }

        if (mc.getOverlay() != null) return;
        uiTick++;
        switch (uiTick) {
            case 4 -> {
                FixtureChecks.verifyAddons();
                FixtureChecks.capture("hud-en");
                FixtureChecks.verifyShortcuts(mc);
                FixtureChecks.openSettings();
            }
            case 12 -> {
                FixtureChecks.capture("settings-en");
                FixtureChecks.switchLanguage(mc, "zh_cn");
            }
            case 40 -> {
                FixtureChecks.capture("settings-zh");
                mc.setScreen(null);
                mc.getDebugOverlay().toggleFpsCharts();
            }
            case 46 -> {
                FixtureChecks.capture("hud-zh");
                FixtureChecks.pressDebugChord(mc, GLFW.GLFW_KEY_PAGE_DOWN);
            }
            case 50 -> {
                FixtureChecks.capture("page-zh");
                setReduced(mc, true);
            }
            case 56 -> {
                if (!mc.showOnlyReducedInfo()) throw new IllegalStateException("Server reduced rule did not reach client");
                FixtureChecks.capture("reduced-zh");
                mc.getDebugOverlay().toggleOverlay();
                closedCounter = counter;
            }
            case 62 -> {
                if (closedCounter != counter) throw new IllegalStateException("DebugText ran with the overlay closed");
                finished = true;
                setReduced(mc, false);
                FixtureChecks.restore();
                LOGGER.info("F3ATHER_VISUAL passed");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void setReduced(Minecraft mc, boolean reduced) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getGameRules().getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(reduced, server));
    }

    private static void reset() {
        warmupAt = sampleAt = previousFrame = columnsCollected = 0;
        frames = 0;
        FRAME_INTERVALS.clear();
        OVERLAY_TIMES.clear();
    }

    private static void updateMeasurement(long now) {
        if (finished || rich) return;
        if (warmupAt == 0) warmupAt = now;
        if (sampleAt == 0 && now - warmupAt >= WARMUP_NANOS) sampleAt = now;
        if (sampleAt == 0) return;
        frames++;
        if (previousFrame != 0) FRAME_INTERVALS.add(now - previousFrame);
        previousFrame = now;
    }

    private static void finish(String mode) {
        Collections.sort(FRAME_INTERVALS);
        Collections.sort(OVERLAY_TIMES);
        double seconds = (previousFrame - sampleAt) / 1_000_000_000.0;
        LOGGER.info("F3ATHER_MEASUREMENT {}", String.format(Locale.ROOT,
                "mode=%s seconds=%.2f frames=%d collections/column/s=%.2f hud_p50_ms=%.3f hud_p95_ms=%.3f frame_p50_ms=%.2f frame_p95_ms=%.2f",
                mode, seconds, frames, columnsCollected / (2 * seconds), percentile(OVERLAY_TIMES, 0.50),
                percentile(OVERLAY_TIMES, 0.95), percentile(FRAME_INTERVALS, 0.50), percentile(FRAME_INTERVALS, 0.95)));
    }

    private static double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return Double.NaN;
        int index = Math.clamp((int) Math.ceil(values.size() * percentile) - 1, 0, values.size() - 1);
        return values.get(index) / 1_000_000.0;
    }

    public static void collectedColumn() {
        if (sampleAt != 0 && !finished && !rich) columnsCollected++;
    }

    public static void startOverlay() {
        start = System.nanoTime();
    }

    public static void endOverlay() {
        if (sampleAt != 0 && !finished && !rich) OVERLAY_TIMES.add(System.nanoTime() - start);
    }
}
