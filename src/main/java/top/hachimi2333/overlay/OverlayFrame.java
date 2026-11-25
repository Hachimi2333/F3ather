package top.hachimi2333.overlay;

import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static top.hachimi2333.overlay.VanillaEntry.*;

record OverlayFrame(boolean hasAddons, Map<String, List<String>> groups, List<String> leftAddons, List<String> rightAddons) {
    static final OverlayFrame EMPTY = new OverlayFrame(false, Map.of(), List.of(), List.of());

    // A null tab means discovery may change which tab is selected on this very frame.
    static OverlayFrame collect(AddonEntries catalog, AddonEntries.Column left, AddonEntries.Column right,
                                String tab, boolean preview) {
        boolean groupAddons = preview || !BuiltIn.containsId(tab);
        boolean listAddons = preview || tab == null || BuiltIn.MODS.id().equals(tab);
        boolean groupStock = groupAddons || (!listAddons && !BuiltIn.VANILLA.id().equals(tab));
        var groups = groupStock ? new HashMap<String, List<String>>() : null;
        var leftLines = listAddons ? new ArrayList<String>() : null;
        var rightLines = listAddons ? new ArrayList<String>() : null;
        boolean foundLeft = catalog.collect(left, groups, leftLines, groupAddons);
        boolean foundRight = catalog.collect(right, groups, rightLines, groupAddons);
        return new OverlayFrame(foundLeft || foundRight, groups == null ? Map.of() : groups,
                leftLines == null ? List.of() : leftLines, rightLines == null ? List.of() : rightLines);
    }

    Map<Region, List<String>> rows(String active, OverlaySettings settings) {
        if (active == null) return Map.of();
        if (active.equals(BuiltIn.SIMPLE.id())) {
            return Map.of(Region.TOP_LEFT, join(FPS, XYZ, BLOCK_POSITION, CHUNK_RELATIVE, FACING, BIOME));
        }
        if (active.equals(BuiltIn.PERFORMANCE.id())) {
            return Map.of(Region.TOP_LEFT, join(FPS, SERVER, RENDERED_CHUNKS, RENDERED_ENTITIES),
                    Region.TOP_RIGHT, join(MEMORY, CPU, DISPLAY, GPU));
        }
        if (active.equals(BuiltIn.WORLD.id())) {
            return Map.of(Region.TOP_LEFT, join(DIMENSION, XYZ, BLOCK_POSITION, CHUNK_POSITION,
                            CHUNK_RELATIVE, FACING, LIGHT_LEVEL, HEIGHTMAPS, BIOME, DIFFICULTY, SPAWNING),
                    Region.TOP_RIGHT, join(TARGET_BLOCK, TARGET_FLUID, TARGET_ENTITY));
        }
        if (active.equals(BuiltIn.MODS.id())) {
            return Map.of(Region.TOP_LEFT, leftAddons, Region.TOP_RIGHT, rightAddons);
        }
        Tab tab = settings.customTab(active);
        return tab == null ? Map.of() : customRows(tab);
    }

    private Map<Region, List<String>> customRows(Tab tab) {
        var rows = new EnumMap<Region, List<String>>(Region.class);
        for (Region area : Region.values()) {
            rows.put(area, tab.entries(area).stream()
                    .flatMap(id -> groups.getOrDefault(id, List.of()).stream()).toList());
        }
        return rows;
    }

    private List<String> join(VanillaEntry... entries) {
        var result = new ArrayList<String>();
        for (VanillaEntry entry : entries) result.addAll(groups.getOrDefault(entry.id(), List.of()));
        return result;
    }
}
