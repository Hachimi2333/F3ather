package top.hachimi2333.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Shared translucent-glass visual tokens, vector pixel icons, and lightweight widget painters
 * for F3ather's native Minecraft configuration screens.
 */
final class MinimalUi {
    static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("f3ather", "textures/gui/logo.png");

    static final int BACKDROP_DIM = 0x520d1014;
    static final int MODAL_DIM = 0x76090b0e;
    static final int GLASS_BG = 0xd614171c;
    static final int MODAL_BG = 0xee161a20;
    static final int TRACK_BG = 0x48090b0e;
    static final int INPUT_BG = 0x700a0c0f;
    static final int GLASS_BORDER = 0x28ffffff;
    static final int MODAL_BORDER = 0x38ffffff;
    static final int HAIRLINE = 0x16ffffff;
    static final int ROW_HOVER = 0x12ffffff;
    static final int SUBTLE_FILL = 0x1cffffff;
    static final int SUBTLE_HOVER = 0x2cffffff;

    static final int TEXT_PRIMARY = 0xfff1f5f4;
    static final int TEXT_MUTED = 0xff8e9b9b;
    static final int TEXT_DISABLED = 0xff5a6363;

    static final int ACCENT = 0xff9ce2d8;
    static final int ACCENT_SOFT = 0x269ce2d8;
    static final int ACCENT_HOVER = 0x389ce2d8;
    static final int ACCENT_BORDER = 0x849ce2d8;

    static final int DANGER = 0xffe89999;
    static final int DANGER_SOFT = 0x28e89999;
    static final int DANGER_BORDER = 0x88e89999;

    static final int SWITCH_WIDTH = 24;
    static final int SWITCH_HEIGHT = 12;

    private MinimalUi() {}

    enum ButtonStyle {
        GHOST, SUBTLE, ACCENT, DANGER
    }

    enum Icon {
        EDIT, RENAME, DELETE, SEARCH, CHEVRON_LEFT, CHEVRON_RIGHT, CLOSE
    }

    static void drawGlassPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.renderOutline(x, y, width, height, border);
        if (width > 2 && height > 2) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x10ffffff);
        }
    }

    static void drawFloatingPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        if (width <= 0 || height <= 0) return;
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, 0x1c000000);
        graphics.fill(x + 1, y + height + 1, x + width + 1, y + height + 2, 0x24000000);
        drawGlassPanel(graphics, x, y, width, height, fill, border);
    }

    static void drawSwitch(GuiGraphics graphics, int x, int y, boolean enabled, boolean hovered, boolean focused) {
        int fill = enabled ? (hovered ? ACCENT_HOVER : ACCENT_SOFT) : (hovered ? SUBTLE_FILL : TRACK_BG);
        int border = focused ? TEXT_PRIMARY : enabled ? ACCENT_BORDER : (hovered ? 0x44ffffff : GLASS_BORDER);
        graphics.fill(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, fill);
        graphics.renderOutline(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, border);

        int knobSize = SWITCH_HEIGHT - 6;
        int knobX = enabled ? x + SWITCH_WIDTH - knobSize - 3 : x + 3;
        int knobY = y + 3;
        graphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, enabled ? ACCENT : (hovered ? TEXT_PRIMARY : TEXT_MUTED));
    }

    static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label,
                           ButtonStyle style, boolean hovered, boolean focused, boolean enabled) {
        int fill = switch (style) {
            case GHOST -> hovered && enabled ? ROW_HOVER : 0x00000000;
            case SUBTLE -> hovered && enabled ? SUBTLE_HOVER : SUBTLE_FILL;
            case ACCENT -> !enabled ? SUBTLE_FILL : hovered ? ACCENT_HOVER : ACCENT_SOFT;
            case DANGER -> !enabled ? SUBTLE_FILL : hovered ? 0x3ce89999 : DANGER_SOFT;
        };
        int border = switch (style) {
            case GHOST -> focused ? ACCENT_BORDER : 0x00000000;
            case SUBTLE -> focused ? ACCENT_BORDER : (hovered && enabled ? 0x38ffffff : HAIRLINE);
            case ACCENT -> !enabled ? HAIRLINE : focused ? TEXT_PRIMARY : ACCENT_BORDER;
            case DANGER -> !enabled ? HAIRLINE : focused ? TEXT_PRIMARY : DANGER_BORDER;
        };
        int textColor = !enabled ? TEXT_DISABLED : switch (style) {
            case GHOST, SUBTLE -> hovered ? TEXT_PRIMARY : (style == ButtonStyle.GHOST ? TEXT_MUTED : TEXT_PRIMARY);
            case ACCENT -> ACCENT;
            case DANGER -> DANGER;
        };

        if ((fill >>> 24) != 0) graphics.fill(x, y, x + width, y + height, fill);
        if ((border >>> 24) != 0) graphics.renderOutline(x, y, width, height, border);

        String trimmed = ellipsize(font, label, Math.max(0, width - 10));
        int textX = x + (width - font.width(trimmed)) / 2;
        graphics.drawString(font, trimmed, textX, baseline(font, y, height), textColor, false);
    }

    static void drawIconButton(GuiGraphics graphics, Icon icon, int x, int y, int size,
                               boolean hovered, boolean focused, boolean destructive) {
        int fill = destructive ? (hovered ? DANGER_SOFT : 0x00000000) : (hovered ? ROW_HOVER : 0x00000000);
        int border = focused ? ACCENT_BORDER : (hovered && destructive ? DANGER_BORDER : 0x00000000);
        int color = destructive ? (hovered ? DANGER : TEXT_MUTED) : (hovered ? TEXT_PRIMARY : TEXT_MUTED);
        if ((fill >>> 24) != 0) graphics.fill(x, y, x + size, y + size, fill);
        if ((border >>> 24) != 0) graphics.renderOutline(x, y, size, size, border);
        drawIcon(graphics, icon, x + (size - 9) / 2, y + (size - 9) / 2, color);
    }

    static void drawIcon(GuiGraphics graphics, Icon icon, int x, int y, int color) {
        switch (icon) {
            case EDIT -> {
                for (int step = 0; step < 6; step++) {
                    graphics.fill(x + 1 + step, y + 6 - step, x + 3 + step, y + 8 - step, color);
                }
                graphics.fill(x + 7, y, x + 9, y + 2, color);
                graphics.fill(x, y + 8, x + 2, y + 9, color);
            }
            case RENAME -> {
                graphics.fill(x + 2, y, x + 7, y + 1, color);
                graphics.fill(x + 4, y + 1, x + 5, y + 8, color);
                graphics.fill(x + 2, y + 8, x + 7, y + 9, color);
            }
            case DELETE, CLOSE -> {
                for (int step = 0; step < 7; step++) {
                    graphics.fill(x + 1 + step, y + 1 + step, x + 2 + step, y + 2 + step, color);
                    graphics.fill(x + 7 - step, y + 1 + step, x + 8 - step, y + 2 + step, color);
                }
            }
            case SEARCH -> {
                graphics.fill(x + 2, y + 1, x + 5, y + 2, color);
                graphics.fill(x + 2, y + 5, x + 5, y + 6, color);
                graphics.fill(x + 1, y + 2, x + 2, y + 5, color);
                graphics.fill(x + 5, y + 2, x + 6, y + 5, color);
                graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
                graphics.fill(x + 6, y + 6, x + 8, y + 8, color);
            }
            case CHEVRON_LEFT -> {
                for (int step = 0; step < 4; step++) {
                    graphics.fill(x + 5 - step, y + 1 + step, x + 6 - step, y + 2 + step, color);
                    graphics.fill(x + 2 + step, y + 4 + step, x + 3 + step, y + 5 + step, color);
                }
            }
            case CHEVRON_RIGHT -> {
                for (int step = 0; step < 4; step++) {
                    graphics.fill(x + 3 + step, y + 1 + step, x + 4 + step, y + 2 + step, color);
                    graphics.fill(x + 6 - step, y + 4 + step, x + 7 - step, y + 5 + step, color);
                }
            }
        }
    }

    static void drawInputFrame(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        graphics.fill(x, y, x + width, y + height, INPUT_BG);
        graphics.renderOutline(x, y, width, height, focused ? ACCENT_BORDER : GLASS_BORDER);
    }

    static EditBox createPlainInput(Font font, int x, int y, int width, int height, Component hint, int maxLength) {
        var box = new EditBox(font, x + 8, baseline(font, y, height), Math.max(1, width - 16),
                Math.max(1, font.lineHeight + 2), hint);
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(hint);
        box.setTextColor(TEXT_PRIMARY);
        box.setTextColorUneditable(TEXT_MUTED);
        box.setTextShadow(false);
        return box;
    }

    static void drawScrollbar(GuiGraphics graphics, int trackX, int trackY, int trackHeight,
                              int contentHeight, int scrollOffset) {
        if (contentHeight <= trackHeight || trackHeight <= 4) return;
        int thumbHeight = Math.max(18, (int) ((long) trackHeight * trackHeight / contentHeight));
        int maxScroll = contentHeight - trackHeight;
        int thumbY = trackY + (int) ((long) (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll));
        graphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, HAIRLINE);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0x609ce2d8);
    }

    static int baseline(Font font, int y, int height) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int available = maxWidth - font.width("…");
        return available <= 0 ? "" : font.plainSubstrByWidth(text, available) + "…";
    }

    static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static void playClick() {
        var mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
