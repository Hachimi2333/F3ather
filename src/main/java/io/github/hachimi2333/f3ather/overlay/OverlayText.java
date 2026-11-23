package io.github.hachimi2333.f3ather.overlay;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class OverlayText {
    private OverlayText() {}

    /** Identify vanilla strings before the event, so a mod can add its own row with the same prefix. */
    public static Map<String, VanillaEntry> identifyVanilla(List<String> lines, boolean left, String demoLine) {
        Map<String, VanillaEntry> vanilla = new IdentityHashMap<>();
        VanillaEntry target = null;
        int graphicsDetails = 0;
        for (String raw : lines) {
            if (raw == null || raw.isEmpty()) {
                target = null;
                graphicsDetails = 0;
                continue;
            }

            String line = AddonEntries.stripFormatting(raw);
            VanillaEntry entry = VanillaEntry.matchPrefix(line, left);
            if (left && entry == null) {
                entry = identifyGameLine(line);
            }
            if (!left) {
                VanillaEntry heading = targetHeading(line);
                if (heading != null) {
                    target = heading;
                    entry = target;
                } else if (entry == null && target != null) {
                    entry = target;
                } else if (entry == VanillaEntry.DISPLAY) {
                    graphicsDetails = 2;
                } else if (entry == null && graphicsDetails > 0) {
                    entry = graphicsDetails == 2 ? VanillaEntry.GPU : VanillaEntry.OPENGL;
                    graphicsDetails--;
                } else if (demoLine != null && demoLine.equals(raw)) {
                    entry = VanillaEntry.DEMO;
                }
            }

            if (entry != null) {
                vanilla.put(raw, entry);
            }
        }
        return vanilla;
    }

    public static List<String> filter(List<String> lines, Map<String, VanillaEntry> vanilla, boolean left,
                                      OverlaySettings settings, AddonEntries addons) {
        List<String> filtered = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && !line.isEmpty() && !shows(line, vanilla, left, settings, addons)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(lines.subList(0, i));
                }
                continue;
            }
            if (filtered != null) {
                filtered.add(line);
            }
        }
        if (filtered == null) {
            return lines;
        }
        return compactGaps(filtered);
    }

    private static boolean shows(String line, Map<String, VanillaEntry> vanilla, boolean left,
                                 OverlaySettings settings, AddonEntries addons) {
        VanillaEntry entry = vanilla.get(line);
        if (entry != null) {
            return settings.shows(entry.id(), entry.minimum());
        }
        String id = addons.identify(line, left, settings);
        return settings.shows(id, OverlaySettings.BuiltIn.ORIGINAL);
    }

    private static List<String> compactGaps(List<String> lines) {
        List<String> compact = new ArrayList<>(lines.size());
        boolean gap = false;
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                gap = !compact.isEmpty();
            } else {
                if (gap) {
                    compact.add("");
                }
                compact.add(line);
                gap = false;
            }
        }
        return compact;
    }

    private static VanillaEntry identifyGameLine(String line) {
        if (!line.isEmpty() && Character.isDigit(line.charAt(0)) && line.contains(" fps")) {
            return VanillaEntry.FPS;
        }
        if (line.startsWith("\"") && line.contains("\" server")) {
            return VanillaEntry.SERVER;
        }
        if (line.contains(" FC: ") && line.indexOf(':') > 0) {
            return VanillaEntry.DIMENSION;
        }
        return null;
    }

    private static VanillaEntry targetHeading(String line) {
        if (line.startsWith("Targeted Block: ")) {
            return VanillaEntry.TARGET_BLOCK;
        }
        if (line.startsWith("Targeted Fluid: ")) {
            return VanillaEntry.TARGET_FLUID;
        }
        if (line.equals("Targeted Entity")) {
            return VanillaEntry.TARGET_ENTITY;
        }
        return null;
    }
}
