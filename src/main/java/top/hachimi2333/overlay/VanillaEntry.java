package top.hachimi2333.overlay;

import java.util.Locale;

import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.COMMON;
import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.LIGHT;
import static top.hachimi2333.overlay.OverlaySettings.BuiltIn.ORIGINAL;

public enum VanillaEntry {
    FPS(LIGHT),
    XYZ(LIGHT, "XYZ: "),
    CHUNK_RELATIVE(LIGHT, "Chunk-relative: "),
    FACING(LIGHT, "Facing: "),
    BIOME(LIGHT, "Biome: "),
    BLOCK_POSITION(COMMON, "Block: "),
    CHUNK_POSITION(COMMON, "Chunk: ", "Waiting for chunk..."),
    DIMENSION(COMMON),
    LIGHT_LEVEL(COMMON, "Client Light: ", "Server Light: "),
    TARGET_BLOCK(COMMON),
    TARGET_FLUID(COMMON),
    TARGET_ENTITY(COMMON),
    VERSION(ORIGINAL, "Minecraft "),
    SERVER(ORIGINAL, "Integrated server @ "),
    RENDERED_CHUNKS(ORIGINAL, "C: "),
    RENDERED_ENTITIES(ORIGINAL, "E: "),
    PARTICLES(ORIGINAL, "P: "),
    CLIENT_CHUNKS(ORIGINAL, "Client Chunk Cache: "),
    SERVER_CHUNKS(ORIGINAL, "ServerChunkCache: ", "Server Chunk Cache: "),
    HEIGHTMAPS(ORIGINAL, "CH ", "SH "),
    DIFFICULTY(ORIGINAL, "Local Difficulty: "),
    BLENDING(ORIGINAL, "Blending: "),
    WORLD_GENERATION(ORIGINAL, "NoiseRouter ", "Biome builder "),
    SPAWNING(ORIGINAL, "SC: "),
    POST_EFFECT(ORIGINAL, "Shader: "),
    SOUND(ORIGINAL, "Sounds: "),
    DEBUG_CHART_HINT(ORIGINAL, "Debug charts: "),
    DEBUG_HELP(ORIGINAL, "For help: press F3 + Q"),
    DEMO(ORIGINAL),
    JAVA(ORIGINAL, "Java: "),
    MEMORY(ORIGINAL, "Mem: "),
    ALLOCATION_RATE(ORIGINAL, "Allocation rate: "),
    ALLOCATED(ORIGINAL, "Allocated: "),
    CPU(ORIGINAL, "CPU: "),
    DISPLAY(ORIGINAL, "Display: "),
    GPU(ORIGINAL),
    OPENGL(ORIGINAL);

    private static final VanillaEntry[] ENTRIES = values();

    private final String translationName = name().toLowerCase(Locale.ROOT);
    private final String id = "vanilla:" + translationName;
    private final OverlaySettings.BuiltIn minimum;
    private final String[] prefixes;

    VanillaEntry(OverlaySettings.BuiltIn minimum, String... prefixes) {
        this.minimum = minimum;
        this.prefixes = prefixes;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "f3ather.entry." + translationName;
    }

    public OverlaySettings.BuiltIn minimum() {
        return minimum;
    }

    static VanillaEntry matchPrefix(String line, boolean left) {
        for (VanillaEntry entry : ENTRIES) {
            if (left != (entry.ordinal() < DEMO.ordinal())) {
                continue;
            }
            for (String prefix : entry.prefixes) {
                if (line.startsWith(prefix)) {
                    return entry;
                }
            }
        }
        return null;
    }
}
