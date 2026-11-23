package io.github.hachimi2333.f3ather;

import io.github.hachimi2333.f3ather.overlay.AddonEntries;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = F3ather.MOD_ID, dist = Dist.CLIENT)
public final class F3ather {
    public static final String MOD_ID = "f3ather";
    public static final AddonEntries ADDONS = new AddonEntries();

    public static OverlaySettings settings() {
        return OverlaySettings.DEFAULT;
    }
}
