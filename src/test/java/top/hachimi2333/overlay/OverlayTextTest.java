package top.hachimi2333.overlay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import static top.hachimi2333.overlay.OverlaySettings.Preset;
import static top.hachimi2333.overlay.OverlaySettings.Refresh;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayTextTest {
    private static final List<String> LEFT = List.of(
            "Minecraft 1.21.1 (neoforge)", "144 fps T: inf", "",
            "XYZ: 1 / 64 / 2", "Block: 1 64 2 [1 0 2]", "Chunk: 0 4 0",
            "Facing: south", "Client Light: 15", "Biome: minecraft:plains", "", "For help: press F3 + Q"
    );

    @Test
    void lightDisplaysOnlyItsEntriesAndCompactsGaps() {
        assertEquals(List.of("144 fps T: inf", "", "XYZ: 1 / 64 / 2", "Facing: south", "Biome: minecraft:plains"),
                draw(LEFT, true, OverlaySettings.DEFAULT, new AddonEntries()));
    }

    @Test
    void reducedDebugCanOnlyShowCoordinatesSuppliedByTheGame() {
        List<String> reduced = List.of("Minecraft 1.21.1", "144 fps T: inf", "", "Chunk-relative: 1 0 2");
        assertEquals(List.of("144 fps T: inf", "", "Chunk-relative: 1 0 2"),
                draw(reduced, true, OverlaySettings.DEFAULT, new AddonEntries()));
    }

    @Test
    void targetSectionsKeepTheirPropertiesAndTagsTogether() {
        List<String> right = List.of("Java: 21", "", "§nTargeted Block: 1, 64, 2",
                "minecraft:oak_leaves", "persistent: §afalse", "#minecraft:leaves",
                "", "§nTargeted Fluid: 1, 64, 2", "minecraft:water", "", "§nTargeted Entity",
                "minecraft:pig", "#minecraft:other");
        var custom = new OverlaySettings(Preset.CUSTOM, Refresh.LIMITED, BuiltIn.COMMON,
                Map.of(VanillaEntry.TARGET_FLUID.id(), false, VanillaEntry.TARGET_ENTITY.id(), false));
        assertEquals(right.subList(2, 6), draw(right, false, custom, new AddonEntries()));
    }

    @Test
    void additionalLabelAfterTargetDetailsGetsItsOwnSwitch() {
        List<String> right = List.of("§nTargeted Entity", "minecraft:pig", "#minecraft:tags",
                "Fixture metrics: 7");
        var custom = new OverlaySettings(Preset.CUSTOM, Refresh.LIVE, BuiltIn.LIGHT,
                Map.of("addon:right:Fixture metrics", true));
        assertEquals(List.of("Fixture metrics: 7"), draw(right, false, custom, new AddonEntries()));
    }

    @Test
    void originalRetainsAllRowsAndSpacing() {
        var original = new OverlaySettings(Preset.ORIGINAL, Refresh.LIVE, BuiltIn.LIGHT, Map.of());
        var lines = new ArrayList<>(LEFT);
        lines.add(null);
        lines.add("Example mod: 100");
        assertSame(lines, draw(lines, true, original, new AddonEntries()));
    }

    @Test
    void additionsAndReplacingRowsAreHandledAfterEventMutations() {
        var lines = new ArrayList<>(LEFT);
        var vanilla = OverlayText.identifyVanilla(lines, true, null);
        lines.remove("XYZ: 1 / 64 / 2");
        lines.addFirst("XYZ: provided by a mod");
        lines.addFirst("Fixture status: ready");
        lines.remove("Biome: minecraft:plains");

        var addons = new AddonEntries();
        var custom = new OverlaySettings(Preset.CUSTOM, Refresh.LIVE, BuiltIn.LIGHT,
                Map.of("addon:left:Fixture status", true));
        assertEquals(List.of("Fixture status: ready", "144 fps T: inf", "", "Facing: south"),
                OverlayText.filter(lines, vanilla, true, custom, addons));
        assertTrue(addons.labels().containsKey("addon:left:XYZ"));
    }

    @Test
    void addonVisibilityFollowsLabelsAndColumnsAcrossValueChanges() {
        var addons = new AddonEntries();
        var custom = new OverlaySettings(Preset.CUSTOM, Refresh.LIVE, BuiltIn.LIGHT,
                Map.of("addon:right:负载", true));
        List<String> initial = List.of("§a负载：17", "Unlabelled 123");
        List<String> changed = List.of("§e负载：52", "Unlabelled 456");
        assertEquals(List.of(initial.getFirst()), OverlayText.filter(initial, Map.of(), false, custom, addons));
        assertEquals(List.of(changed.getFirst()), OverlayText.filter(changed, Map.of(), false, custom, addons));
        assertEquals(List.of(), OverlayText.filter(changed, Map.of(), true, custom, addons));
        assertEquals(Map.of("addon:left:负载", "负载"), addons.labels());
    }

    @Test
    void discoveryIsBoundedAndSavedOverridesStillWork() {
        var addons = new AddonEntries();
        for (int i = 0; i < AddonEntries.LIMIT; i++) {
            addons.identify("Fixture " + i + ": 123", true, OverlaySettings.DEFAULT);
        }
        assertEquals(AddonEntries.otherId(true), addons.identify("Overflow: 123", true, OverlaySettings.DEFAULT));
        assertEquals(AddonEntries.LIMIT, addons.labels().size());

        var custom = new OverlaySettings(Preset.CUSTOM, Refresh.LIVE, BuiltIn.LIGHT,
                Map.of("addon:left:Overflow", true));
        assertEquals(List.of("Overflow: 456"),
                OverlayText.filter(List.of("Overflow: 456"), Map.of(), true, custom, addons));
        assertFalse(addons.labels().containsKey("addon:left:Overflow"));
    }

    private static List<String> draw(List<String> lines, boolean left, OverlaySettings settings, AddonEntries addons) {
        return OverlayText.filter(lines, OverlayText.identifyVanilla(lines, left, null), left, settings, addons);
    }
}
