package top.hachimi2333.config;

import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Mode;
import top.hachimi2333.overlay.OverlaySettings.Preset;
import top.hachimi2333.overlay.OverlaySettings.Refresh;
import top.hachimi2333.overlay.VanillaEntry;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final String SHORTCUT_KEYS = "JKMORXYZ";
    private static final ModConfigSpec.IntValue SCHEMA;
    private static final ModConfigSpec.EnumValue<Mode> MODE;
    private static final ModConfigSpec.ConfigValue<String> SHORTCUT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ORDER;
    private static final ModConfigSpec.EnumValue<Preset> PRESET;
    private static final ModConfigSpec.EnumValue<BuiltIn> LEGACY_BASE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LEGACY_SHOWN;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LEGACY_HIDDEN;

    private static volatile OverlaySettings current = OverlaySettings.DEFAULT;

    static {
        var builder = new ModConfigSpec.Builder();
        SCHEMA = builder.defineInRange("schema", 0, 0, 1);
        MODE = builder.defineEnum("mode", Mode.F3ATHER);
        SHORTCUT = builder.define("shortcut", "J", value -> value instanceof String key
                && (key.isEmpty() || key.length() == 1 && SHORTCUT_KEYS.contains(key)));
        ENABLED = builder.defineListAllowEmpty("enabled", List.copyOf(OverlaySettings.DEFAULT.enabled()),
                VanillaEntry.FPS::id, ClientConfig::validId);
        ORDER = builder.defineListAllowEmpty("order", List.of(), VanillaEntry.FPS::id, ClientConfig::validId);
        PRESET = builder.defineEnum("preset", Preset.LIGHT);
        builder.defineEnum("refresh", Refresh.LIMITED);
        builder.push("custom");
        LEGACY_BASE = builder.defineEnum("base", BuiltIn.LIGHT);
        LEGACY_SHOWN = builder.defineListAllowEmpty("shown", List.of(), VanillaEntry.FPS::id, ClientConfig::validId);
        LEGACY_HIDDEN = builder.defineListAllowEmpty("hidden", List.of(), VanillaEntry.FPS::id, ClientConfig::validId);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static OverlaySettings current() { return current; }

    public static synchronized void reload(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        var oldAddons = new LinkedHashMap<String, Boolean>();
        if (SCHEMA.get() == 0) {
            Preset preset = PRESET.get();
            BuiltIn base = preset == Preset.CUSTOM ? LEGACY_BASE.get() : BuiltIn.valueOf(preset.name());
            var enabled = new TreeSet<>(OverlaySettings.defaults(base));
            if (base == BuiltIn.ORIGINAL) {
                oldAddons.put("addon:left:", true);
                oldAddons.put("addon:right:", true);
            }
            if (preset == Preset.CUSTOM) {
                migrate(LEGACY_SHOWN.get(), true, enabled, oldAddons);
                migrate(LEGACY_HIDDEN.get(), false, enabled, oldAddons);
            }
            save(new OverlaySettings(preset == Preset.ORIGINAL ? Mode.TRADITIONAL : Mode.F3ATHER,
                    SHORTCUT.get(), enabled, List.of(), oldAddons));
            return;
        }
        LEGACY_SHOWN.get().forEach(id -> oldAddons.put(id, true));
        LEGACY_HIDDEN.get().forEach(id -> oldAddons.put(id, false));
        current = new OverlaySettings(MODE.get(), SHORTCUT.get(), Set.copyOf(ENABLED.get()),
                List.copyOf(ORDER.get()), oldAddons);
    }

    private static void migrate(List<? extends String> ids, boolean visible, Set<String> enabled,
                                Map<String, Boolean> addons) {
        for (String id : ids) {
            if (id.startsWith("addon:")) {
                addons.put(id, visible);
            } else if (VanillaEntry.IDS.contains(id)) {
                if (visible) enabled.add(id);
                else enabled.remove(id);
            }
        }
    }

    public static synchronized void save(OverlaySettings settings) {
        MODE.set(settings.mode());
        SHORTCUT.set(settings.shortcut());
        ENABLED.set(new ArrayList<>(new TreeSet<>(settings.enabled())));
        ORDER.set(settings.order());
        LEGACY_SHOWN.set(legacyIds(settings, true));
        LEGACY_HIDDEN.set(legacyIds(settings, false));
        SCHEMA.set(1);
        current = settings;
        SPEC.save();
    }

    private static List<String> legacyIds(OverlaySettings settings, boolean visible) {
        return settings.legacyAddons().entrySet().stream()
                .filter(entry -> entry.getValue() == visible)
                .map(Map.Entry::getKey).sorted().toList();
    }

    private static boolean validId(Object value) {
        return value instanceof String id && id.length() <= 220
                && (id.startsWith("vanilla:") || id.startsWith("addon:"));
    }
}
