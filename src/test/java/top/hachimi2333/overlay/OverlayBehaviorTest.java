package top.hachimi2333.overlay;

import org.junit.jupiter.api.Test;
import top.hachimi2333.overlay.AddonEntries.Column;
import top.hachimi2333.overlay.AddonEntries.Row;
import top.hachimi2333.overlay.AddonEntries.Source;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        var appended = new ArrayList<String>();
        assertTrue(addons.collect(first, grouped, appended));
        assertEquals(List.of("Edited"), grouped.get("vanilla:facing"));
        assertEquals(List.of("Line: 1", "状態"), appended);
        assertEquals(2, addons.entries().size());
        assertEquals(List.of("FPS", "North"), new Column(true, rows, caller -> owner));
    }

    @Test
    void duplicatesFromDifferentModsStayIndependent() {
        var active = new AtomicReference<>(new Source("one", "First"));
        var rows = new Column(true, List.of(), caller -> active.get());
        rows.add("Status: ready");
        active.set(new Source("two", "Second"));
        rows.add("Status: ready");
        rows.add(0, rows.remove(1));

        var addons = new AddonEntries();
        var grouped = new LinkedHashMap<String, List<String>>();
        var appended = new ArrayList<String>();
        addons.collect(rows, grouped, appended);
        var first = addons.entries().get(0);
        var second = addons.entries().get(1);
        assertNotEquals(first.id(), second.id());
        assertEquals("Second", first.source().name());
        assertEquals("First", second.source().name());
        assertEquals(List.of("Status: ready", "Status: ready"), appended);
        assertEquals(List.of(first.id(), second.id()), List.copyOf(grouped.keySet()));
    }

    @Test
    void catalogRemainsBoundedWithoutDroppingDebugText() {
        var addons = new AddonEntries();
        var rows = new Column(true, List.of(), caller -> new Source("example", "Example"));
        for (int i = 0; i < 1200; i++) rows.add("Label " + i + ": text");
        var appended = new ArrayList<String>();
        addons.collect(rows, new LinkedHashMap<>(), appended);
        assertTrue(addons.entries().size() <= 514);
        assertEquals(rows, appended);
    }

    @Test
    void tabCycleSkipsHiddenTabsAndWraps() {
        var custom = Tab.create("Redstone");
        var settings = OverlaySettings.DEFAULT.withBuiltIn(BuiltIn.SIMPLE, false)
                .withBuiltIn(BuiltIn.WORLD, false).withTab(custom);
        var tabs = settings.visibleTabs(false);
        assertEquals(List.of("performance", "vanilla", custom.id()), tabs);
        assertEquals("performance", OverlaySettings.cycle(tabs, null));
        assertEquals("vanilla", OverlaySettings.cycle(tabs, "performance"));
        assertEquals("performance", OverlaySettings.cycle(tabs, custom.id()));
        assertEquals(List.of("performance", "mods", "vanilla", custom.id()), settings.visibleTabs(true));
        assertNull(OverlaySettings.cycle(List.of(), "performance"));
    }

    @Test
    void dragMovesAnEntryAndPreservesOrderAcrossCorners() {
        var tab = Tab.create("Redstone")
                .move("vanilla:fps", Region.TOP_LEFT, 0)
                .move("vanilla:xyz", Region.TOP_LEFT, 1)
                .move("vanilla:light_level", Region.BOTTOM_RIGHT, 0);
        tab = tab.move("vanilla:fps", Region.TOP_LEFT, 2);
        assertEquals(List.of("vanilla:xyz", "vanilla:fps"), tab.entries(Region.TOP_LEFT));
        tab = tab.move("vanilla:xyz", Region.BOTTOM_RIGHT, 0);
        assertEquals(List.of("vanilla:fps"), tab.entries(Region.TOP_LEFT));
        assertEquals(List.of("vanilla:xyz", "vanilla:light_level"), tab.entries(Region.BOTTOM_RIGHT));
        assertFalse(tab.remove("vanilla:xyz").contains("vanilla:xyz"));
    }

    @Test
    void longListsPageWithoutLosingLinesAndBottomRegionsStayBottomAligned() {
        var lines = java.util.stream.IntStream.range(0, 99).mapToObj(Integer::toString).toList();
        var rows = Map.of(Region.BOTTOM_RIGHT, lines);
        var area = HudLayout.bounds(320, 18, 120, Region.BOTTOM_RIGHT, rows, true);
        int pages = HudLayout.pageCount(320, 18, 120, 120, rows, true);
        assertEquals(20, pages);
        assertEquals(109, area.firstY(false, 1));
        var result = new ArrayList<String>();
        for (int page = 0; page < pages; page++) result.addAll(HudLayout.page(lines, area, page));
        assertEquals(lines, result);
    }

    @Test
    void samplerMaintainsRateAndRefreshesImmediatelyOnInvalidation() {
        var clock = new OverlayHud.SampleClock();
        assertTrue(clock.due(-500, 10));
        assertFalse(clock.due(99_999_499, 10));
        assertTrue(clock.due(110_000_000, 10));
        assertFalse(clock.due(199_999_499, 10));
        assertTrue(clock.due(199_999_500, 10));
        clock.invalidate();
        assertTrue(clock.due(220_000_000, 10));
        assertTrue(clock.due(220_000_001, 0));
        assertTrue(clock.due(220_000_002, 0));
    }

    @Test
    void rawRowsKeepReducedCoordinatesAndTargetGroups() {
        var reduced = List.of("Minecraft 1.21.1", "60 fps", "server", "C: 1", "E: 1", "P: 0", "Cache",
                "", "Chunk-relative: 1 2 3", "", "Debug charts: ...", "For help: press F3 + Q");
        var left = VanillaEntry.label(reduced, true, true, false);
        assertEquals("vanilla:fps", left.get(1).id());
        assertEquals(new Row("vanilla:chunk_relative", "Chunk-relative: 1 2 3"), left.get(8));
        var full = List.of("Java: 21", "Display: 1920x1080", "GPU name", "4.6.0", "",
                "§nTargeted Block: 1, 2, 3", "minecraft:lever", "powered: §atrue", "", "§nTargeted Entity", "minecraft:cow");
        var right = VanillaEntry.label(full, false, false, false);
        assertEquals(new Row("vanilla:target_block", "powered: §atrue"), right.get(7));
        assertEquals(new Row("vanilla:target_entity", "minecraft:cow"), right.getLast());
    }
}
