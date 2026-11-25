package top.hachimi2333.overlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record OverlaySettings(int refreshHz, boolean compatibility, boolean disableAnimations, String lockedTab,
                              Set<BuiltIn> enabled, List<Tab> customTabs) {
    public static final List<Integer> REFRESH_PRESETS = List.of(1, 5, 10, 20, 0);
    public static final int DEFAULT_REFRESH_HZ = 10;
    public static final OverlaySettings DEFAULT = new OverlaySettings(DEFAULT_REFRESH_HZ, false, false, "",
            EnumSet.allOf(BuiltIn.class), List.of());

    public OverlaySettings {
        if (!REFRESH_PRESETS.contains(refreshHz)) refreshHz = DEFAULT_REFRESH_HZ;
        if (lockedTab == null) lockedTab = "";
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

    public String effectiveLockedTab(boolean modsDetected) {
        if (lockedTab.isEmpty()) return "";
        BuiltIn builtIn = BuiltIn.byId(lockedTab);
        if (builtIn != null) {
            if (!enabled.contains(builtIn)) return "";
            if (builtIn == BuiltIn.MODS && !modsDetected) return "";
            return builtIn.id();
        }
        Tab custom = customTab(lockedTab);
        return custom != null && custom.enabled() ? custom.id() : "";
    }

    public Tab customTab(String id) {
        return customTabs.stream().filter(tab -> tab.id().equals(id)).findFirst().orElse(null);
    }

    public OverlaySettings withRefresh(int hz) {
        return new OverlaySettings(hz, compatibility, disableAnimations, lockedTab, enabled, customTabs);
    }

    public OverlaySettings withCompatibility(boolean value) {
        return new OverlaySettings(refreshHz, value, disableAnimations, lockedTab, enabled, customTabs);
    }

    public OverlaySettings withDisableAnimations(boolean value) {
        return new OverlaySettings(refreshHz, compatibility, value, lockedTab, enabled, customTabs);
    }

    public OverlaySettings withLockedTab(String id) {
        if (id == null || id.isBlank()) {
            return new OverlaySettings(refreshHz, compatibility, disableAnimations, "", enabled, customTabs);
        }
        BuiltIn builtIn = BuiltIn.byId(id);
        if (builtIn != null) {
            var updatedEnabled = EnumSet.noneOf(BuiltIn.class);
            updatedEnabled.addAll(enabled);
            updatedEnabled.add(builtIn);
            return new OverlaySettings(refreshHz, compatibility, disableAnimations, builtIn.id(),
                    updatedEnabled, customTabs);
        }
        Tab existing = customTab(id);
        if (existing != null) {
            if (!existing.enabled()) {
                var updatedTabs = new ArrayList<>(customTabs);
                for (int i = 0; i < updatedTabs.size(); i++) {
                    if (updatedTabs.get(i).id().equals(id)) {
                        updatedTabs.set(i, existing.toggle(true));
                        break;
                    }
                }
                return new OverlaySettings(refreshHz, compatibility, disableAnimations, id, enabled, updatedTabs);
            }
            return new OverlaySettings(refreshHz, compatibility, disableAnimations, id, enabled, customTabs);
        }
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, "", enabled, customTabs);
    }

    public OverlaySettings withBuiltIn(BuiltIn tab, boolean value) {
        var updated = EnumSet.noneOf(BuiltIn.class);
        updated.addAll(enabled);
        if (value) updated.add(tab);
        else updated.remove(tab);
        String nextLock = !value && tab.id().equals(lockedTab) ? "" : lockedTab;
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, nextLock, updated, customTabs);
    }

    public OverlaySettings withTab(Tab tab) {
        String nextLock = !tab.enabled() && tab.id().equals(lockedTab) ? "" : lockedTab;
        var updated = new ArrayList<>(customTabs);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(tab.id())) {
                updated.set(i, tab);
                return new OverlaySettings(refreshHz, compatibility, disableAnimations, nextLock, enabled, updated);
            }
        }
        updated.add(tab);
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, nextLock, enabled, updated);
    }

    public OverlaySettings withoutTab(String id) {
        String nextLock = id != null && id.equals(lockedTab) ? "" : lockedTab;
        return new OverlaySettings(refreshHz, compatibility, disableAnimations, nextLock, enabled,
                customTabs.stream().filter(tab -> !tab.id().equals(id)).toList());
    }

    static String cycle(List<String> tabs, String active) {
        if (tabs.isEmpty()) return null;
        if (active == null) return tabs.getFirst();
        return tabs.get((tabs.indexOf(active) + 1) % tabs.size());
    }

    public enum BuiltIn {
        SIMPLE, PERFORMANCE, WORLD, MODS, VANILLA;

        private final String id = name().toLowerCase(java.util.Locale.ROOT);

        public String id() { return id; }

        private static final Set<String> IDS = Arrays.stream(values()).map(BuiltIn::id)
                .collect(Collectors.toUnmodifiableSet());

        public static BuiltIn byId(String id) {
            if (id == null) return null;
            for (BuiltIn tab : values()) {
                if (tab.id.equals(id)) return tab;
            }
            return null;
        }

        static boolean containsId(String id) { return id != null && IDS.contains(id); }
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
