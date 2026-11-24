package top.hachimi2333.overlay;

import org.junit.jupiter.api.Test;
import top.hachimi2333.overlay.AddonEntries.Column;
import top.hachimi2333.overlay.AddonEntries.Row;
import top.hachimi2333.overlay.AddonEntries.Source;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Mode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class OverlayBehaviorTest {
    @Test
    void changesOnOneFrameDoNotChangeTheFollowingFrame() {
        var rows = List.of(new Row("vanilla:fps", "FPS"), new Row("vanilla:facing", "North"));
        var owner = new Source("example", "Example");
        var first = new Column(true, rows, caller -> owner);
        Collections.rotate(first, 1);
        first.set(0, "Edited");
        first.subList(1, 2).clear();
        first.addAll(List.of("Line: 1", "状態"));
        first.add(null);

        var addons = new AddonEntries();
        var grouped = new LinkedHashMap<String, List<String>>();
        addons.collect(first, grouped, OverlaySettings.DEFAULT);
        assertEquals(Map.of("vanilla:facing", List.of("Edited")), grouped);
        assertEquals(2, addons.entries().size());
        assertEquals(List.of("FPS", "North"), new Column(true, rows, caller -> owner));
    }

    @Test
    void sameLabelsFromDifferentModsAreSeparatelyConfigurable() {
        var active = new AtomicReference<>(new Source("one", "First"));
        var rows = new Column(true, List.of(), caller -> active.get());
        rows.add("Status: ready");
        active.set(new Source("two", "Second"));
        rows.add("Status: ready");
        var addons = new AddonEntries();
        var grouped = new LinkedHashMap<String, List<String>>();
        addons.collect(rows, grouped, OverlaySettings.DEFAULT);
        var first = addons.entries().get(0);
        var second = addons.entries().get(1);
        assertNotEquals(first.id(), second.id());
        assertEquals("First", first.source().name());
        assertEquals("Second", second.source().name());
        assertTrue(grouped.isEmpty());

        var settings = new OverlaySettings(Mode.F3ATHER, "J", Set.of(second.id()),
                List.of(second.id(), first.id()), Map.of(first.legacyId(), true));
        addons.collect(rows, grouped, settings);
        assertEquals(List.of(second.id()), List.copyOf(grouped.keySet()));
        assertEquals(List.of(second.id(), first.id()), settings.ordered(List.of(first.id(), second.id())));
    }

    @Test
    void catalogStaysBoundedAndOldLightPresetStillMatchesDefaults() {
        var addons = new AddonEntries();
        var rows = new Column(true, List.of(), caller -> new Source("example", "Example"));
        for (int i = 0; i < 1200; i++) rows.add("Label " + i + ": text");
        addons.collect(rows, new LinkedHashMap<>(), OverlaySettings.DEFAULT);
        assertTrue(addons.entries().size() <= 514);
        assertEquals(OverlaySettings.defaults(BuiltIn.LIGHT), OverlaySettings.DEFAULT.enabled());
        assertFalse(VanillaEntry.XYZ.allowedInReduced());
        assertTrue(VanillaEntry.CHUNK_RELATIVE.allowedInReduced());
    }
}
