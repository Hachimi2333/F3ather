package io.github.hachimi2333.f3ather_fixture;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;
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
    private static long warmupAt;
    private static long sampleAt;
    private static long previousFrame;
    private static long columnsCollected;
    private static long linesDrawn;
    private static long addOnLinesDrawn;
    private static int frames;
    private static int counter;
    private static boolean finished;

    public FixtureClient() {
        NeoForge.EVENT_BUS.addListener(FixtureClient::addDebugText);
        NeoForge.EVENT_BUS.addListener(FixtureClient::tick);
    }

    private static void addDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        updateMeasurement(System.nanoTime());
        List<String> left = event.getLeft();
        List<String> right = event.getRight();
        verifyFresh(left);
        verifyFresh(right);

        left.removeIf(line -> line != null && line.startsWith("For help: "));
        left.replaceAll(line -> line != null && line.startsWith("Debug charts: ")
                ? MARKER + "charts: " + counter : line);
        Collections.rotate(right, 1);
        for (int i = 0; i < 48; i++) {
            left.add(MARKER + "left " + i + ": " + (counter + i));
            right.add(MARKER + "right " + i + ": " + (counter - i));
        }
        right.add(MARKER + "status ready");
        counter++;
    }

    private static void verifyFresh(List<String> lines) {
        for (String line : lines) {
            if (line != null && line.startsWith(MARKER)) {
                throw new IllegalStateException("Previous DebugText mutations leaked into a later frame");
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.screen == null
                && Boolean.getBoolean("f3ather.fixture.auto") && !minecraft.getDebugOverlay().showDebugScreen()) {
            minecraft.getDebugOverlay().toggleOverlay();
        }
        if (!finished && sampleAt != 0 && System.nanoTime() - sampleAt >= SAMPLE_NANOS) {
            finish();
            if (Boolean.getBoolean("f3ather.fixture.auto")) {
                minecraft.stop();
            }
        }
    }

    private static void updateMeasurement(long now) {
        if (finished) {
            return;
        }
        if (warmupAt == 0) {
            warmupAt = now;
        }
        if (sampleAt == 0 && now - warmupAt >= WARMUP_NANOS) {
            sampleAt = now;
        }
        if (sampleAt != 0) {
            frames++;
            if (previousFrame != 0) {
                FRAME_INTERVALS.add(now - previousFrame);
            }
            previousFrame = now;
        }
    }

    private static void finish() {
        finished = true;
        Collections.sort(FRAME_INTERVALS);
        double seconds = (previousFrame - sampleAt) / 1_000_000_000.0;
        LOGGER.info("F3ATHER_MEASUREMENT {}", String.format(Locale.ROOT,
                "seconds=%.2f frames=%d collections/column/s=%.2f rows/frame=%.2f addon_rows/frame=%.2f p50_ms=%.2f p95_ms=%.2f",
                seconds, frames, columnsCollected / (2 * seconds), (double) linesDrawn / frames,
                (double) addOnLinesDrawn / frames, percentile(0.50), percentile(0.95)));
    }

    private static double percentile(double percentile) {
        if (FRAME_INTERVALS.isEmpty()) {
            return Double.NaN;
        }
        int index = Math.clamp((int) Math.ceil(FRAME_INTERVALS.size() * percentile) - 1, 0,
                FRAME_INTERVALS.size() - 1);
        return FRAME_INTERVALS.get(index) / 1_000_000.0;
    }

    public static void collectedColumn() {
        if (sampleAt != 0 && !finished) {
            columnsCollected++;
        }
    }

    public static void drawLines(List<String> lines) {
        if (sampleAt == 0 || finished) {
            return;
        }
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                linesDrawn++;
                if (line.startsWith(MARKER)) {
                    addOnLinesDrawn++;
                }
            }
        }
    }
}
