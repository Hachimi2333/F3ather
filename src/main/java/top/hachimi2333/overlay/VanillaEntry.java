package top.hachimi2333.overlay;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.COMMON;
import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.LIGHT;
import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.ORIGINAL;

public enum VanillaEntry {
    FPS(LIGHT, true),
    XYZ(LIGHT),
    CHUNK_RELATIVE(LIGHT, true),
    FACING(LIGHT),
    BIOME(LIGHT),
    BLOCK_POSITION(COMMON),
    CHUNK_POSITION(COMMON),
    DIMENSION(COMMON),
    LIGHT_LEVEL(COMMON),
    TARGET_BLOCK(COMMON),
    TARGET_FLUID(COMMON),
    TARGET_ENTITY(COMMON),
    VERSION(ORIGINAL, true),
    SERVER(ORIGINAL, true),
    RENDERED_CHUNKS(ORIGINAL, true),
    RENDERED_ENTITIES(ORIGINAL, true),
    PARTICLES(ORIGINAL, true),
    CLIENT_CHUNKS(ORIGINAL, true),
    SERVER_CHUNKS(ORIGINAL),
    HEIGHTMAPS(ORIGINAL),
    DIFFICULTY(ORIGINAL),
    BLENDING(ORIGINAL),
    WORLD_GENERATION(ORIGINAL),
    SPAWNING(ORIGINAL),
    POST_EFFECT(ORIGINAL),
    SOUND(ORIGINAL),
    DEBUG_CHART_HINT(ORIGINAL, true),
    DEBUG_HELP(ORIGINAL, true),
    DEMO(ORIGINAL, true),
    JAVA(ORIGINAL, true),
    MEMORY(ORIGINAL, true),
    ALLOCATION_RATE(ORIGINAL, true),
    ALLOCATED(ORIGINAL, true),
    CPU(ORIGINAL, true),
    DISPLAY(ORIGINAL, true),
    GPU(ORIGINAL, true),
    OPENGL(ORIGINAL, true),
    GRAPHICS(ORIGINAL, true);

    public static final List<VanillaEntry> ALL = List.of(values());
    public static final List<String> IDS = Arrays.stream(values()).map(VanillaEntry::id).toList();

    private final String name = name().toLowerCase(Locale.ROOT);
    private final String id = "vanilla:" + name;
    private final OverlaySettings.BuiltIn minimum;
    private final boolean allowedInReduced;

    VanillaEntry(OverlaySettings.BuiltIn minimum) {
        this(minimum, false);
    }

    VanillaEntry(OverlaySettings.BuiltIn minimum, boolean allowedInReduced) {
        this.minimum = minimum;
        this.allowedInReduced = allowedInReduced;
    }

    public String id() { return id; }
    public String translationKey() { return "f3ather.entry." + name; }
    public String descriptionKey() { return "f3ather.description." + name; }
    public OverlaySettings.BuiltIn minimum() { return minimum; }
    public boolean allowedInReduced() { return allowedInReduced; }
    public boolean rightColumn() { return ordinal() >= DEMO.ordinal() || this == TARGET_BLOCK
            || this == TARGET_FLUID || this == TARGET_ENTITY; }
    public boolean slow() { return ordinal() >= JAVA.ordinal(); }
}
