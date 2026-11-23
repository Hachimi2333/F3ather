package io.github.hachimi2333.f3ather.config;

import io.github.hachimi2333.f3ather.overlay.AddonEntries;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.BuiltIn;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.Preset;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.Refresh;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<Preset> PRESET;
    private static final ModConfigSpec.EnumValue<Refresh> REFRESH;
    private static final ModConfigSpec.EnumValue<BuiltIn> CUSTOM_BASE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SHOWN;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN;

    private static volatile OverlaySettings current = OverlaySettings.DEFAULT;

    static {
        var builder = new ModConfigSpec.Builder();
        PRESET = builder.defineEnum("preset", Preset.LIGHT);
        REFRESH = builder.defineEnum("refresh", Refresh.LIMITED);
        builder.push("custom");
        CUSTOM_BASE = builder.defineEnum("base", BuiltIn.LIGHT);
        SHOWN = builder.defineListAllowEmpty("shown", List.<String>of(), ClientConfig::isEntryId);
        HIDDEN = builder.defineListAllowEmpty("hidden", List.<String>of(), ClientConfig::isEntryId);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static OverlaySettings current() {
        return current;
    }

    public static synchronized void reload(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            Map<String, Boolean> overrides = new HashMap<>();
            SHOWN.get().forEach(id -> overrides.put(id, true));
            HIDDEN.get().forEach(id -> overrides.put(id, false));
            current = new OverlaySettings(PRESET.get(), REFRESH.get(), CUSTOM_BASE.get(), overrides);
        }
    }

    public static synchronized void save(OverlaySettings settings) {
        PRESET.set(settings.preset());
        REFRESH.set(settings.refresh());
        CUSTOM_BASE.set(settings.customBase());
        SHOWN.set(idsWithValue(settings.overrides(), true));
        HIDDEN.set(idsWithValue(settings.overrides(), false));
        SPEC.save();
        current = settings;
    }

    private static List<String> idsWithValue(Map<String, Boolean> overrides, boolean visible) {
        return overrides.entrySet().stream()
                .filter(entry -> entry.getValue() == visible)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static boolean isEntryId(Object value) {
        return value instanceof String id && id.length() <= 100
                && (id.startsWith("vanilla:") || AddonEntries.isAddonId(id));
    }
}
