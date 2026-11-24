package top.hachimi2333.overlay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record OverlaySettings(Mode mode, String shortcut, Set<String> enabled, List<String> order,
                              Map<String, Boolean> legacyAddons) {
    public static final OverlaySettings DEFAULT = new OverlaySettings(Mode.F3ATHER, "J",
            defaults(BuiltIn.LIGHT), List.of(), Map.of());

    public OverlaySettings {
        enabled = Set.copyOf(enabled);
        order = List.copyOf(new LinkedHashSet<>(order));
        legacyAddons = Map.copyOf(legacyAddons);
    }

    public boolean showsAddon(String id, String legacyId, String legacyOther) {
        if (order.contains(id)) {
            return enabled.contains(id);
        }
        return enabled.contains(id)
                || legacyAddons.getOrDefault(legacyId, legacyAddons.getOrDefault(legacyOther, false));
    }

    public List<String> ordered(List<String> available) {
        var remaining = new LinkedHashSet<>(available);
        var result = new ArrayList<String>(remaining.size());
        for (String id : order) {
            if (remaining.remove(id)) {
                result.add(id);
            }
        }
        result.addAll(remaining);
        return result;
    }

    public static Set<String> defaults(BuiltIn base) {
        var result = new LinkedHashSet<String>();
        for (VanillaEntry entry : VanillaEntry.values()) {
            if (base.ordinal() >= entry.minimum().ordinal()) {
                result.add(entry.id());
            }
        }
        return result;
    }

    public enum Mode { F3ATHER, TRADITIONAL }
    public enum Preset { LIGHT, COMMON, ORIGINAL, CUSTOM }
    public enum BuiltIn { LIGHT, COMMON, ORIGINAL }
    public enum Refresh { LIMITED, LIVE }
}
