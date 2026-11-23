package io.github.hachimi2333.f3ather.overlay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OverlayCacheTest {
    private final Object level = new Object();
    private final Object camera = new Object();
    private final OverlayCache cache = new OverlayCache();

    @Test
    void limitedSamplesTenTimesAndKeepsPerFrameEventListsIndependent() {
        AtomicInteger collectors = new AtomicInteger();
        AddonEntries addons = new AddonEntries();
        var context = context(OverlaySettings.DEFAULT, false);
        for (int frame = 0; frame < 100; frame++) {
            var state = cache.begin(frame * 10_000_000L, context);
            List<String> left = state.collect(true, () -> {
                collectors.incrementAndGet();
                return new ArrayList<>(List.of("60 fps", "XYZ: 1 / 64 / 2"));
            }, () -> null);
            assertEquals(2, left.size());
            left.add("Additional counter: " + frame);
            assertEquals(List.of("60 fps", "XYZ: 1 / 64 / 2"), state.forDrawing(left, true, addons));
            assertEquals(3, left.size());
        }
        assertEquals(10, collectors.get());
    }

    @Test
    void liveSamplesEveryFrameAndModeChangeReplacesOldSnapshots() {
        AtomicInteger collectors = new AtomicInteger();
        var limited = context(OverlaySettings.DEFAULT, false);
        var settings = new OverlaySettings(OverlaySettings.Preset.LIGHT, OverlaySettings.Refresh.LIVE,
                OverlaySettings.BuiltIn.LIGHT, Map.of());
        var live = context(settings, false);
        collect(cache.begin(0, limited), collectors);
        collect(cache.begin(1, limited), collectors);
        collect(cache.begin(2, live), collectors);
        collect(cache.begin(3, live), collectors);
        collect(cache.begin(4, limited), collectors);
        assertEquals(4, collectors.get());
    }

    @Test
    void invalidationRecollectsOnReducedDebugWorldCameraAndReopen() {
        AtomicInteger collectors = new AtomicInteger();
        var normal = context(OverlaySettings.DEFAULT, false);
        collect(cache.begin(0, normal), collectors);
        collect(cache.begin(1, context(OverlaySettings.DEFAULT, true)), collectors);
        var newWorld = new OverlayCache.Context(new Object(), camera, true, 0, OverlaySettings.DEFAULT);
        collect(cache.begin(2, newWorld), collectors);
        var newCamera = new OverlayCache.Context(newWorld.level(), new Object(), true, 0, OverlaySettings.DEFAULT);
        collect(cache.begin(3, newCamera), collectors);
        cache.invalidate();
        collect(cache.begin(4, newCamera), collectors);
        assertEquals(5, collectors.get());
    }

    @Test
    void unrelatedDrawCallsRetainTheirOwnLists() {
        var frame = cache.begin(0, context(OverlaySettings.DEFAULT, false));
        collect(frame, new AtomicInteger());
        List<String> unrelated = List.of("Another HUD: 1");
        assertSame(unrelated, frame.forDrawing(unrelated, true, new AddonEntries()));
    }

    private OverlayCache.Context context(OverlaySettings settings, boolean reduced) {
        return new OverlayCache.Context(level, camera, reduced, 0, settings);
    }

    private static void collect(OverlayCache.Frame frame, AtomicInteger count) {
        frame.collect(true, () -> {
            count.incrementAndGet();
            return new ArrayList<>(List.of("60 fps"));
        }, () -> null);
    }
}
