package top.hachimi2333.overlay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record OverlaySettings(int refreshHz, boolean compatibility, boolean disableAnimations,
                              Set<BuiltIn> enabled, List<Tab> customTabs) {
    public static final List<Integer> REFRESH_PRESETS = List.of(1, 5, 10, 20, 0);
    public static final int DEFAULT_REFRESH_HZ = 10;
    public static final OverlaySettings DEFAULT = new OverlaySettings(DEFAULT_REFRESH_HZ, false, false,
            EnumSet.allOf(BuiltIn.class), List.of());

    public OverlaySettings {
        if (!REFRESH_PRESETS.contains(refreshHz)) refreshHz = DEFAULT_REFRESH_HZ;
        enabled = Set.copyOf(enabled);
        customTabs = List.copyOf(customTabs);
    }

    public List<String> visibleTabs(boolean modsDetected) {
        var result = new ArrayList<String>();
        for (BuiltIn tab : BuiltIn.values()) {
            if (enabled.contains(tab) && (tab != BuiltIn.MODS || modsDetected)) result.add(tab.id());
        }
        for (Tab tab : customTabs) {
            if (tab.enabled()) result.add(tab.id());
        }
        return result;
    }

    public Tab customTab(String id) {
        return customTabs.stream().filter(tab -> tab.id().equals(id)).findFirst().orElse(null);
    }

    public OverlaySettings withRefresh(int hz) {
        return new OverlaySettings(hz, compatibility, disableAnimations, enabled, customTabs);
    }

    public OverlaySettings withCompatibility(boolean value) {
        return new OverlaySettings(refreshHz, value, disableAnimations, enabled, customTabs);
    }

    public OverlaySettings withDisableAnimations(boolean value) {
        return new OverlaySettings(refreshHz, compatibility, value, enabled, customTabs);
    }

    public OverlaySettings withBuiltIn(BuiltIn tab, boolean value) {
        var updated = EnumSet.noneOf(BuiltIn.class);
        updated.addAll(enabled);
        if (value) updated.add(tab);
        else updated.remove(tab);
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, updated, customTabs);
    }

    public OverlaySettings withTab(Tab tab) {
        var updated = new ArrayList<>(customTabs);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(tab.id())) {
                updated.set(i, tab);
                return new OverlaySettings(refreshHz, compatibility, disableAnimations, enabled, updated);
            }
        }
        updated.add(tab);
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, enabled, updated);
    }

    public OverlaySettings withoutTab(String id) {
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, enabled,
                customTabs.stream().filter(tab -> !tab.id().equals(id)).toList());
    }

    static String cycle(List<String> tabs, String active) {
        if (tabs.isEmpty()) return null;
        if (active == null) return tabs.getFirst();
        return tabs.get((tabs.indexOf(active) + 1) % tabs.size());
    }

    public enum BuiltIn {
        SIMPLE, PERFORMANCE, WORLD, MODS, VANILLA;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Region {
        TOP_LEFT, BOTTOM_LEFT, TOP_RIGHT, BOTTOM_RIGHT;

        public boolean left() { return this == TOP_LEFT || this == BOTTOM_LEFT; }
        public boolean top() { return this == TOP_LEFT || this == TOP_RIGHT; }
    }

    public record Tab(String id, String name, boolean enabled, Map<Region, List<String>> regions) {
        public Tab {
            var seen = new LinkedHashSet<String>();
            var normalized = new EnumMap<Region, List<String>>(Region.class);
            for (Region region : Region.values()) {
                normalized.put(region, regions.getOrDefault(region, List.of()).stream().filter(seen::add).toList());
            }
            regions = Map.copyOf(normalized);
        }

        public static Tab create(String name) {
            return new Tab(UUID.randomUUID().toString(), name, true, Map.of());
        }

        public List<String> entries(Region region) { return regions.get(region); }

        public boolean contains(String entry) {
            return regions.values().stream().anyMatch(entries -> entries.contains(entry));
        }

        public Tab rename(String value) { return new Tab(id, value, enabled, regions); }
        public Tab toggle(boolean value) { return new Tab(id, name, value, regions); }

        public Tab remove(String entry) {
            var updated = new EnumMap<Region, List<String>>(Region.class);
            regions.forEach((region, ids) -> updated.put(region, ids.stream().filter(id -> !id.equals(entry)).toList()));
            return new Tab(id, name, enabled, updated);
        }

        /** The destination index refers to the list before the item is removed. */
        public Tab move(String entry, Region destination, int index) {
            int previous = entries(destination).indexOf(entry);
            if (previous >= 0 && previous < index) index--;
            var updated = new EnumMap<>(remove(entry).regions);
            var entries = new ArrayList<>(updated.get(destination));
            entries.add(Math.clamp(index, 0, entries.size()), entry);
            updated.put(destination, entries);
            return new Tab(id, name, enabled, updated);
        }
    }
}
