package top.hachimi2333.config;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Card;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.state.Signal;
import com.nstut.openui.theme.ColorScheme;
import com.nstut.openui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class SettingsUi {
    static final int BACKGROUND = 0xff17181a;
    static final int SURFACE = 0xff222325;
    static final int DIVIDER = 0xff393b3d;
    static final int TEXT = 0xffe8e8e5;
    static final int MUTED = 0xffa7a9a8;
    static final int ACCENT = 0xff9ab9aa;
    private static final int HOVER = 0xff303234;
    private static final int SELECTED = 0xff2e3a35;
    private static final int INPUT = 0xff191a1c;
    private static final int DISABLED = 0xff737675;
    private static final int DANGER = 0xffe0a09b;
    private static final int CONTROL_HEIGHT = 21;
    private static final int ICON_SIZE = 9;
    private static final Theme DARK = Theme.dark();
    static final Theme THEME = new Theme(
            new ColorScheme(BACKGROUND, SURFACE, SURFACE, HOVER, HOVER, INPUT,
                    DIVIDER, DIVIDER, MUTED, ACCENT, 0xffadc9bb, SELECTED,
                    DANGER, DANGER, 0xff4d3030, ACCENT, ACCENT, ACCENT, ACCENT, SELECTED,
                    BACKGROUND, TEXT, MUTED, DISABLED, SELECTED, 0x00000000),
            DARK.spacing(), DARK.radii(), DARK.durations(), false);

    private SettingsUi() {}

    static Card panel(UIComponent content, int padding) {
        return Ui.card(content).padding(padding).radius(4).elevated(false).outlined(true);
    }

    static Button button(Component label, Runnable action) {
        return new Button(label, action, null, "");
    }

    static Button icon(Component label, Icon icon, Runnable action) {
        var button = new Button(label, action, icon, "");
        button.width(CONTROL_HEIGHT).tooltip(label);
        return button;
    }

    static Button searchResult(String title, String owner, Runnable action) {
        return new Button(Component.literal(title), action, null, owner).leftAligned();
    }

    private static int baseline(Font font, int y, int height) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        int available = width - font.width("…");
        return available < 0 ? "" : font.plainSubstrByWidth(text, available) + "…";
    }

    enum Icon {
        EDIT, RENAME, DELETE;

        void draw(GuiGraphics graphics, int x, int y, int color) {
            switch (this) {
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
                case DELETE -> {
                    for (int step = 0; step < 7; step++) {
                        graphics.fill(x + 1 + step, y + 1 + step, x + 2 + step, y + 2 + step, color);
                        graphics.fill(x + 7 - step, y + 1 + step, x + 8 - step, y + 2 + step, color);
                    }
                }
            }
        }
    }

    static final class Button extends ButtonWidget {
        private final Icon icon;
        private final String detail;
        private boolean leftAligned;
        private boolean selected;
        private boolean filled;
        private boolean destructive;

        Button(Component label, Runnable action, Icon icon, String detail) {
            super(label);
            this.icon = icon;
            this.detail = detail;
            onPress(action);
            height(CONTROL_HEIGHT);
        }

        Button filled() {
            filled = true;
            return this;
        }

        Button destructive() {
            destructive = true;
            return this;
        }

        private Button leftAligned() {
            leftAligned = true;
            return this;
        }

        void selected(boolean value) {
            selected = value;
            invalidatePaint();
        }

        @Override
        public int preferredWidth(Font font) {
            return icon == null ? font.width(getLabel()) + 16 : CONTROL_HEIGHT;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            boolean active = isEnabled();
            boolean hover = active && isHovered();
            int color = !active ? DISABLED : selected ? ACCENT : destructive ? DANGER : TEXT;
            int fill = selected ? SELECTED : hover ? HOVER : filled ? DIVIDER : SURFACE;
            if (isFocused()) UiRender.roundedOutline(graphics, x, y, width, height, 3, fill, ACCENT);
            else if (selected || hover || filled) UiRender.roundedRect(graphics, x, y, width, height, 3, fill);
            if (icon != null) {
                icon.draw(graphics, x + (width - ICON_SIZE) / 2, y + (height - ICON_SIZE) / 2, color);
                return;
            }
            int available = Math.max(0, width - 16);
            String suffix = ellipsize(font, detail, available / 3);
            int detailWidth = suffix.isEmpty() ? 0 : font.width(suffix) + 8;
            String title = ellipsize(font, getLabel().getString(), available - detailWidth);
            int baseline = baseline(font, y, height);
            int labelX = leftAligned ? x + 8 : x + (width - font.width(title)) / 2;
            graphics.drawString(font, title, labelX, baseline, color, false);
            if (detailWidth > 0) graphics.drawString(font, suffix, x + width - detailWidth, baseline, MUTED, false);
        }
    }

    static final class TextInput extends TextField {
        TextInput(Signal<String> value, Font font, Component hint, int limit) {
            super(value, font);
            var input = getEditBox();
            input.setMaxLength(limit);
            input.setHint(hint);
            input.setTextColor(TEXT);
            input.setTextColorUneditable(MUTED);
            input.setTextShadow(false);
            height(23);
        }

        @Override
        public void layout(int x, int y, int width, int height) {
            super.layout(x, y, width, height);
            var input = getEditBox();
            input.setX(x + 7);
            input.setY(baseline(measureFont(), y, height));
            input.setWidth(Math.max(1, width - 14));
            input.setHeight(Math.max(1, height - (input.getY() - y)));
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            var input = getEditBox();
            // The borderless EditBox starts at the text baseline. Its surrounding padding is clickable too.
            double textX = Math.clamp(x, input.getX(), input.getX() + input.getWidth() - 1);
            return super.mouseClicked(textX, Math.max(y, input.getY()), button);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            UiRender.roundedOutline(graphics, x, y, width, height, 3, INPUT, isFocused() ? ACCENT : DIVIDER);
            getEditBox().render(graphics, mouseX, mouseY, delta);
        }
    }
}
