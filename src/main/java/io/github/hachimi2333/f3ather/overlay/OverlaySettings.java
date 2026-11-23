package io.github.hachimi2333.f3ather.overlay;

import java.util.Map;

public record OverlaySettings(
        Preset preset, Refresh refresh, BuiltIn customBase, Map<String, Boolean> overrides
) {
    public static final OverlaySettings DEFAULT =
            new OverlaySettings(Preset.LIGHT, Refresh.LIMITED, BuiltIn.LIGHT, Map.of());

    public OverlaySettings {
        overrides = Map.copyOf(overrides);
    }

    public boolean shows(String id, BuiltIn minimum) {
        if (preset == Preset.CUSTOM) {
            return overrides.getOrDefault(id, customBase.includes(minimum));
        }
        return preset.base.includes(minimum);
    }

    public enum Preset {
        LIGHT(BuiltIn.LIGHT), COMMON(BuiltIn.COMMON), ORIGINAL(BuiltIn.ORIGINAL), CUSTOM(null);

        private final BuiltIn base;

        Preset(BuiltIn base) {
            this.base = base;
        }
    }

    public enum BuiltIn {
        LIGHT, COMMON, ORIGINAL;

        boolean includes(BuiltIn minimum) {
            return ordinal() >= minimum.ordinal();
        }
    }

    public enum Refresh {
        LIMITED, LIVE
    }
}
