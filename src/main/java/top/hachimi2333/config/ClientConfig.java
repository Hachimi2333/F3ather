package top.hachimi2333.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ClientConfig {
    public static final String FILE_NAME = "f3ather-tabs.toml";
    public static final ModConfigSpec SPEC;
    private static final Gson JSON = new Gson();
    private static final ModConfigSpec.IntValue REFRESH;
    private static final ModConfigSpec.BooleanValue COMPATIBILITY;
    private static final ModConfigSpec.BooleanValue DISABLE_ANIMATIONS;
    private static final EnumMap<BuiltIn, ModConfigSpec.BooleanValue> BUILT_INS = new EnumMap<>(BuiltIn.class);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM;
    private static volatile OverlaySettings current = OverlaySettings.DEFAULT;

    static {
        var builder = new ModConfigSpec.Builder();
        REFRESH = builder.comment("Text samples per second. Zero samples every frame.")
                .defineInRange("refreshHz", 10, 0, 60);
        COMPATIBILITY = builder.define("compatibility", false);
        DISABLE_ANIMATIONS = builder.define("disableAnimations", OverlaySettings.DEFAULT.disableAnimations());
        builder.push("builtIn");
        for (BuiltIn tab : BuiltIn.values()) BUILT_INS.put(tab, builder.define(tab.id(), true));
        builder.pop();
        CUSTOM = builder.comment("Custom tabs saved by the in-game layout editor.")
                .defineListAllowEmpty("customTabs", List.of(), () -> "", value -> value instanceof String);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static OverlaySettings current() { return current; }

    public static synchronized void reload(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var enabled = EnumSet.noneOf(BuiltIn.class);
        BUILT_INS.forEach((tab, value) -> { if (value.get()) enabled.add(tab); });
        var tabs = new ArrayList<Tab>();
        var ids = new HashSet<String>();
        for (String encoded : CUSTOM.get()) {
            Tab tab = decodeTab(encoded);
            if (tab != null && ids.add(tab.id())) tabs.add(tab);
        }
        current = new OverlaySettings(REFRESH.get(), COMPATIBILITY.get(), DISABLE_ANIMATIONS.get(), enabled, tabs);
    }

    public static synchronized void save(OverlaySettings settings) {
        if (settings.equals(current)) return;
        REFRESH.set(settings.refreshHz());
        COMPATIBILITY.set(settings.compatibility());
        DISABLE_ANIMATIONS.set(settings.disableAnimations());
        BUILT_INS.forEach((tab, value) -> value.set(settings.enabled().contains(tab)));
        CUSTOM.set(settings.customTabs().stream().map(ClientConfig::encodeTab).toList());
        SPEC.save();
        current = settings;
    }

    static String encodeTab(Tab tab) {
        var result = new JsonObject();
        result.addProperty("id", tab.id());
        result.addProperty("name", tab.name());
        result.addProperty("enabled", tab.enabled());
        var regions = new JsonObject();
        for (Region region : Region.values()) {
            var entries = new JsonArray();
            tab.entries(region).forEach(entries::add);
            regions.add(region.name().toLowerCase(Locale.ROOT), entries);
        }
        result.add("regions", regions);
        return JSON.toJson(result);
    }

    static Tab decodeTab(String encoded) {
        try {
            var value = JsonParser.parseString(encoded).getAsJsonObject();
            String id = UUID.fromString(value.get("id").getAsString()).toString();
            String name = value.get("name").getAsString().strip();
            if (name.isEmpty() || name.length() > 40) return null;
            var regions = new EnumMap<Region, List<String>>(Region.class);
            var storedRegions = value.getAsJsonObject("regions");
            for (Region region : Region.values()) {
                var stored = storedRegions.getAsJsonArray(region.name().toLowerCase(Locale.ROOT));
                if (stored == null) continue;
                var entries = new ArrayList<String>();
                for (var item : stored) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
                    String entry = item.getAsString();
                    if (entry.length() <= 240 && (entry.startsWith("vanilla:") || entry.startsWith("addon:"))) {
                        entries.add(entry);
                    }
                }
                regions.put(region, entries);
            }
            return new Tab(id, name, value.get("enabled").getAsBoolean(), regions);
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException |
                 UnsupportedOperationException | com.google.gson.JsonParseException invalid) {
            return null;
        }
    }
}
