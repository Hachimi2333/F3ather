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
    void sharedReplacementKeepsBothSlotsAndSurvivesReordering() {
        var rows = new Column(true, List.of(new Row("fps", "60"), new Row("biome", "Plains")), null);
        rows.replaceAll(ignored -> "Hidden");
        Collections.swap(rows, 0, 1);
        rows.set(0, "First after swap");
        var groups = new LinkedHashMap<String, List<String>>();
        new AddonEntries().collect(rows, groups, new ArrayList<>());
        assertEquals(List.of("First after swap"), groups.get("biome"));
        assertEquals(List.of("Hidden"), groups.get("fps"));
    }

    @Test
    void listAddResolvesTheListenerWithoutWalkingPastABridge() {
        var caller = new AtomicReference<Class<?>>();
        List<String> rows = new Column(true, List.of(), type -> {
            caller.set(type);
            return AddonEntries.UNKNOWN;
        });
        rows.add("First");
        assertEquals(getClass(), caller.get());
        rows.add(0, "Second");
        assertEquals(getClass(), caller.get());
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
    void vanillaSkipsGroupingButDiscoversRowsAndEditorCanReadTheNextFrame() {
        var addons = new AddonEntries();
        var owner = new Source("example", "Example");
        var left = new Column(true, List.of(new Row("vanilla:fps", "60 fps")), caller -> owner);
        var right = new Column(false, List.of(), caller -> owner);
        right.addAll(List.of("Status: A", "Status: B"));
        var vanilla = OverlayFrame.collect(addons, left, right, BuiltIn.VANILLA.id(), false);
        assertTrue(vanilla.hasAddons());
        assertTrue(vanilla.groups().isEmpty());
        assertTrue(vanilla.rightAddons().isEmpty());
        assertEquals(2, addons.entries().size());
        right.removeFirst();
        var preview = OverlayFrame.collect(addons, left, right, BuiltIn.VANILLA.id(), true);
        assertEquals(List.of("60 fps"), preview.groups().get("vanilla:fps"));
        assertEquals(List.of("Status: B"), preview.groups().get("addon:right:example:Status:1"));
        assertFalse(preview.groups().containsKey("addon:right:example:Status:2"));
        var mods = OverlayFrame.collect(addons, left, right, BuiltIn.MODS.id(), false);
        assertEquals(List.of("Status: B"), mods.rightAddons());
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

    @Test
    void preEventRowsJoinEventAdditionsWithoutShiftingVanillaGroups() {
        var leftTrace = new RawDebugRows();
        String[] game = {"Minecraft 1.21.1", "60 fps", "server", "C: 1", "E: 1", "P: 0", "Cache"};
        leftTrace.initial(game);
        leftTrace.append("ServerChunkCache: 5");
        leftTrace.append("XYZ: 10.0 / 20.0 / 30.0");
        var left = new ArrayList<>(List.of(game));
        left.add(2, "§7p50=§r100 §7p98=§r80 §7p99.5=§r60§7 fps");
        left.add(game[0]);
        left.add("ServerChunkCache: 5");
        left.add("XYZ: 10.0 / 20.0 / 30.0");

        var rightTrace = new RawDebugRows();
        String[] system = {"Java: 21", "Mem: 25%", "Allocation rate: 0", "Allocated: 40%", "",
                "CPU: CPU", "", "Display: 10x10", "GPU name", "4.6.0"};
        rightTrace.initial(system);
        var right = new ArrayList<>(List.of(system));
        right.add(4, "Off-Heap: +25MB");
        right.addAll(List.of("§aSodium Renderer (0.8.13+mc1.21.1)", "Pools: Geometry 194/422 MiB",
                "Injected by another mod: active", "Chunk Builder: Schd=00"));
        for (var line : List.of("", "§nTargeted Block: 1, 2, 3", "minecraft:lever", "powered: §atrue")) {
            rightTrace.append(line);
            right.add(line);
        }
        int tagStart = right.size();
        right.add("#minecraft:mineable/axe");
        rightTrace.appendWithPrefix(right, tagStart, "#");

        var addons = new AddonEntries();
        var source = new Source("fixture", "Fixture");
        var groups = new LinkedHashMap<String, List<String>>();
        var leftAdded = new ArrayList<String>();
        var rightAdded = new ArrayList<String>();
        var leftRows = leftTrace.resolve(left, true, true, false, true);
        var rightRows = rightTrace.resolve(right, false, true, false, true);
        var leftColumn = new Column(true, leftRows, caller -> source);
        var rightColumn = new Column(false, rightRows, caller -> source);
        rightColumn.set(4, "Off-Heap: +26MB");
        rightColumn.add("Event: ready");

        assertTrue(addons.collect(leftColumn, groups, leftAdded));
        assertTrue(addons.collect(rightColumn, groups, rightAdded));
        assertEquals(List.of("server"), groups.get("vanilla:server"));
        assertEquals(List.of("XYZ: 10.0 / 20.0 / 30.0"), groups.get("vanilla:xyz"));
        assertEquals(List.of("GPU name"), groups.get("vanilla:gpu"));
        assertEquals(List.of("#minecraft:mineable/axe"), groups.get("vanilla:target_block").subList(3, 4));
        assertEquals(List.of(left.get(2), game[0]), leftAdded);
        assertEquals(List.of("Off-Heap: +26MB", right.get(11), right.get(12), right.get(13), right.get(14),
                "Event: ready"), rightAdded);
        assertEquals(5, addons.entries().stream().filter(entry -> entry.source().id().equals("sodium")).count());
        assertEquals(2, addons.entries().stream().filter(entry -> entry.source().id().isEmpty()).count());
        assertEquals(right, new Column(false, rightRows, caller -> source));
    }

    @Test
    void stockRowsWithOptionalFieldsDoNotEnableTheModsTab() {
        var trace = new RawDebugRows();
        String[] system = {"Java: 21", "Mem: 25%", "Allocation rate: 0", "Allocated: 40%", "",
                "CPU: CPU", "", "Display: 10x10", "GPU name", "4.6.0"};
        trace.initial(system);
        trace.prepend("演示时间");
        var actual = new ArrayList<>(List.of(system));
        actual.addFirst("演示时间");
        var addons = new AddonEntries();
        var rows = trace.resolve(actual, false, false, true, false);
        var groups = new LinkedHashMap<String, List<String>>();
        boolean detected = addons.collect(new Column(false, rows, null), groups, new ArrayList<>());
        assertFalse(detected);
        assertEquals(List.of("演示时间"), groups.get("vanilla:demo"));
        assertFalse(OverlaySettings.DEFAULT.visibleTabs(detected).contains("mods"));
        assertTrue(addons.entries().isEmpty());
    }

    @Test
    void replacedCollectorStillAllowsEventAdditions() {
        var trace = new RawDebugRows();
        var rows = trace.resolve(List.of("Java: 21"), false, false, false, true);
        var column = new Column(false, rows, caller -> new Source("fixture", "Fixture"));
        column.add("Event row");
        var groups = new LinkedHashMap<String, List<String>>();
        var added = new ArrayList<String>();
        assertTrue(new AddonEntries().collect(column, groups, added));
        assertEquals(List.of("Java: 21"), groups.get("vanilla:java"));
        assertEquals(List.of("Event row"), added);
    }
}
