package top.hachimi2333.config;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.ScrollView;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.minecraft.UiScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.AddonEntries;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.Mode;
import top.hachimi2333.overlay.VanillaEntry;
import top.hachimi2333.render.ModernFont;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class F3atherScreen extends UiScreen {
    private static final int TEXT = 0xffe8f0f7;
    private static final int MUTED = 0xffa4b4c5;
    private static final int ACCENT = 0xff7ae3bd;
    private static final int PANEL = 0xff182330;
    private static final int GAP = 8;

    private final Screen parent;
    private final Set<String> enabled;
    private final List<String> order;
    private final Map<String, AddonEntries.Entry> addons = new HashMap<>();
    private final Map<String, Boolean> legacy;
    private Mode mode;
    private String shortcut;
    private boolean binding;
    private double scrollOffset;
    private ScrollView list;

    public F3atherScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        OverlaySettings settings = ClientConfig.current();
        mode = settings.mode();
        shortcut = settings.shortcut();
        enabled = new HashSet<>(settings.enabled());
        legacy = settings.legacyAddons();
        var available = new ArrayList<>(VanillaEntry.IDS);
        for (AddonEntries.Entry addon : F3ather.ADDONS.entries()) {
            addons.put(addon.id(), addon);
            available.add(addon.id());
            if (settings.showsAddon(addon.id(), addon.legacyId(), addon.legacyOther())) {
                enabled.add(addon.id());
            }
        }
        // Keep settings for modules that have not contributed text in this session.
        order = new ArrayList<>(settings.order());
        for (String id : available) {
            if (!order.contains(id)) order.add(id);
        }
    }

    @Override
    protected int uiLeft() { return Math.max(8, (width - 800) / 2); }

    @Override
    protected int uiWidth() { return width - 2 * uiLeft(); }

    @Override
    protected int uiTop() { return Math.max(7, (height - 530) / 2); }

    @Override
    protected int uiHeight() { return height - 2 * uiTop(); }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        graphics.fill(0, 0, width, height, 0xff10171f);
        int leftWidth = sidebarWidth();
        int top = uiTop() + 26;
        int bottom = uiTop() + uiHeight() - 28;
        if (bottom > top) {
            UiRender.roundedRect(graphics, uiLeft(), top, leftWidth, bottom - top, 7, PANEL);
            UiRender.roundedRect(graphics, uiLeft() + leftWidth + GAP, top,
                    uiWidth() - leftWidth - GAP, bottom - top, 7, PANEL);
        }
        graphics.flush();
        super.render(graphics, x, y, delta);
    }

    private int sidebarWidth() { return Math.clamp(uiWidth() / 4, 77, 145); }

    @Override
    protected UIComponent buildUI() {
        var title = label(text("title"), 17, TEXT).height(20).fillWidth();
        var sidebar = Ui.column(
                label(text("mode"), 9, MUTED).fillWidth(),
                choice("mode_f3ather", Mode.F3ATHER),
                choice("mode_traditional", Mode.TRADITIONAL),
                label(text("shortcut"), 9, MUTED).fillWidth(),
                button(binding ? text("press_letter") : shortcut.isEmpty() ? text("disabled")
                                : text("shortcut_label", shortcut),
                        () -> { binding = true; refresh(); }).fillWidth(),
                button(text("disable_shortcut"), () -> { binding = false; shortcut = ""; refresh(); }).fillWidth()
        ).gap(5).fillWidth();
        if (binding) sidebar.addChild(label(text("allowed_keys", String.join(" · ",
                ClientConfig.SHORTCUT_KEYS.split(""))), 9, MUTED, 3).fillWidth());
        var left = Ui.padding(7, Ui.scroll(sidebar).fillWidth().fillHeight()).width(sidebarWidth()).fillHeight();
        var right = mode == Mode.F3ATHER ? settingsList()
                : Ui.column(label(text("mode_traditional"), 13, TEXT).fillWidth(),
                        label(text("traditional_description"), 10, MUTED, 4).fillWidth()).gap(8);
        if (mode == Mode.TRADITIONAL) list = null;
        var body = Ui.row(left, Ui.padding(7, right.fillWidth().fillHeight()).flex().fillHeight())
                .gap(GAP).fillWidth().flex();
        var save = button(text("save"), () -> {
            ClientConfig.save(new OverlaySettings(mode, shortcut, enabled, order, legacy));
            onClose();
        }).primary().width(68);
        var cancel = button(text("cancel"), this::onClose).width(68);
        var footer = Ui.row(Ui.column().flex(), cancel, save).gap(GAP).fillWidth().height(21);
        return Ui.column(title, body, footer).gap(6).fillWidth().fillHeight();
    }

    private UIComponent settingsList() {
        var rows = Ui.column().gap(4);
        int textWidth = Math.max(30, uiWidth() - sidebarWidth() - 119);
        var titles = new HashMap<String, VanillaEntry>();
        for (VanillaEntry entry : VanillaEntry.ALL) titles.put(entry.id(), entry);
        var visible = order.stream().filter(id -> titles.containsKey(id) || addons.containsKey(id)).toList();
        for (int index = 0; index < visible.size(); index++) {
            String id = visible.get(index);
            var builtin = titles.get(id);
            AddonEntries.Entry addon = addons.get(id);
            Component title = builtin == null
                    ? Component.literal(addon.name().isEmpty() ? addon.sample() : addon.name())
                    : Component.translatable(builtin.translationKey());
            Component description;
            if (builtin != null) {
                description = Component.translatable(builtin.descriptionKey());
            } else {
                Component source = addon.source().id().isEmpty() ? text("source_unknown")
                        : text("source", addon.source().name());
                description = text("addon_description", source, addon.sample());
            }
            int descriptionLines = Math.clamp((int) Math.ceil(ModernFont.INSTANCE.width(
                    description.getString(), 9) / textWidth), 1, 3);
            var copy = Ui.column(label(title, 10.5f, TEXT).fillWidth(),
                    label(description, 9, MUTED, descriptionLines).fillWidth()).gap(1).flex();
            boolean on = enabled.contains(id);
            var toggle = button(text(on ? "on" : "off"), () -> {
                if (!enabled.remove(id)) enabled.add(id);
                refresh();
            });
            toggle.setActive(on);
            toggle.width(41);
            String previous = index == 0 ? null : visible.get(index - 1);
            String next = index == visible.size() - 1 ? null : visible.get(index + 1);
            var up = button(text("move_up", title), "↑", () -> move(id, previous)).enabled(previous != null).width(19);
            var down = button(text("move_down", title), "↓", () -> move(id, next)).enabled(next != null).width(19);
            rows.addChild(Ui.row(copy, toggle, up, down).gap(3)
                    .align(Alignment.CENTER).fillWidth().height(17 + descriptionLines * 11));
        }
        if (addons.isEmpty()) {
            rows.addChild(label(text("discover"), 9, MUTED, 3).fillWidth());
        }
        list = Ui.scroll(rows);
        list.scrollTo(scrollOffset);
        return Ui.column(label(text("items"), 13, ACCENT).fillWidth(),
                list.flex().fillWidth()).gap(4);
    }

    private UIComponent choice(String key, Mode choice) {
        var button = button(text(key), () -> {
            mode = choice;
            binding = false;
            refresh();
        });
        button.setActive(mode == choice);
        return button.fillWidth();
    }

    private void move(String id, String other) {
        if (other == null) return;
        java.util.Collections.swap(order, order.indexOf(id), order.indexOf(other));
        refresh();
    }

    private void refresh() {
        if (list != null) scrollOffset = list.scrollOffset();
        rebuildUI();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (binding) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
            } else if (key >= 'A' && key <= 'Z' && ClientConfig.SHORTCUT_KEYS.indexOf(key) >= 0) {
                shortcut = Character.toString(key);
                binding = false;
                refresh();
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static ButtonWidget button(Component title, Runnable action) {
        return button(title, title.getString(), action);
    }

    private static ButtonWidget button(Component title, String shown, Runnable action) {
        var button = Ui.button(title, action).height(20).radius(4)
                .content(new Label(shown, 9.5f, TEXT, 1, true));
        button.setColors(0xff253546, 0xff365770);
        return button;
    }

    private static UIComponent label(Component text, float size, int color) {
        return label(text, size, color, 1);
    }

    private static UIComponent label(Component text, float size, int color, int maxLines) {
        return new Label(text.getString(), size, color, maxLines, false);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("f3ather.screen." + key, args);
    }

    private static final class Label extends UIComponent {
        private final String text;
        private final float size;
        private final int color;
        private final int maxLines;
        private final boolean centered;
        private List<String> lines = List.of();

        Label(String text, float size, int color, int maxLines, boolean centered) {
            this.text = text;
            this.size = size;
            this.color = color;
            this.maxLines = maxLines;
            this.centered = centered;
        }

        @Override
        public int preferredWidth(Font font) { return (int) Math.ceil(ModernFont.INSTANCE.width(text, size)); }

        @Override
        public int preferredHeight(Font font) { return (int) Math.ceil(size + 2) * maxLines; }

        @Override
        public void layout(int x, int y, int width, int height) {
            super.layout(x, y, width, height);
            ModernFont modern = ModernFont.INSTANCE;
            var parts = new ArrayList<String>(maxLines);
            String remaining = text;
            for (int index = 0; index < maxLines && !remaining.isBlank(); index++) {
                String fitted = modern.fit(remaining, size, width);
                if (fitted.equals(remaining) || index == maxLines - 1 || fitted.length() <= 1) {
                    parts.add(fitted);
                    break;
                }
                int length = fitted.length() - 1;
                int space = remaining.lastIndexOf(' ', length);
                if (space > length / 2) length = space;
                parts.add(remaining.substring(0, length).stripTrailing());
                remaining = remaining.substring(length).stripLeading();
            }
            lines = parts;
        }

        @Override
        public void render(GuiGraphics graphics, Font vanillaFont, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            ModernFont modern = ModernFont.INSTANCE;
            float lineY = centered ? y + Math.max(0, (height - size) / 2) : y;
            for (String line : lines) {
                float lineX = centered ? x + Math.max(0, (width - modern.width(line, size)) / 2) : x;
                modern.draw(graphics, line, lineX, lineY, size, color);
                lineY += size + 2;
            }
            graphics.flush();
        }
    }
}
