package io.github.hachimi2333.f3ather.config;

import io.github.hachimi2333.f3ather.overlay.AddonEntries;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.BuiltIn;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.Preset;
import io.github.hachimi2333.f3ather.overlay.OverlaySettings.Refresh;
import io.github.hachimi2333.f3ather.overlay.VanillaEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class F3atherScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 85;
    private static final int FOOTER_HEIGHT = 33;

    private final Screen parent;
    private final AddonEntries addons;
    private final Map<String, Boolean> overrides;
    private Preset preset;
    private Refresh refresh;
    private BuiltIn customBase;
    private BuiltIn resetBase;
    private CycleButton<BuiltIn> baseButton;
    private Button resetButton;
    private EntryList entries;

    public F3atherScreen(Screen parent, AddonEntries addons) {
        super(text("screen.title"));
        this.parent = parent;
        this.addons = addons;
        OverlaySettings settings = ClientConfig.current();
        preset = settings.preset();
        refresh = settings.refresh();
        customBase = settings.customBase();
        resetBase = customBase;
        overrides = new HashMap<>(settings.overrides());
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(width - 20, 408);
        int start = (width - contentWidth) / 2;
        int columnWidth = (contentWidth - 8) / 2;
        int right = start + columnWidth + 8;

        int listHeight = Math.max(0, height - LIST_TOP - FOOTER_HEIGHT);
        entries = addRenderableWidget(new EntryList(minecraft, width, listHeight, contentWidth));

        addRenderableWidget(CycleButton.builder((Preset value) -> option("preset", value))
                .withValues(Preset.values()).withInitialValue(preset)
                .create(start, 23, columnWidth, 20, text("screen.preset"), (button, value) -> {
                    preset = value;
                    updateEntries();
                }));
        addRenderableWidget(CycleButton.builder((Refresh value) -> option("refresh", value))
                .withValues(Refresh.values()).withInitialValue(refresh)
                .create(right, 23, columnWidth, 20, text("screen.refresh"), (button, value) -> refresh = value));
        baseButton = addRenderableWidget(CycleButton.builder((BuiltIn value) -> option("preset", value))
                .withValues(BuiltIn.values()).withInitialValue(resetBase)
                .create(start, 46, columnWidth, 20, text("screen.reset_base"), (button, value) -> resetBase = value));
        resetButton = addRenderableWidget(Button.builder(text("screen.reset"), ignored -> {
            customBase = resetBase;
            overrides.clear();
            updateEntries();
        }).bounds(right, 46, columnWidth, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> {
            ClientConfig.save(snapshot());
            onClose();
        }).bounds(start, height - 27, columnWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
                .bounds(right, height - 27, columnWidth, 20).build());
        updateEntries();
    }

    private OverlaySettings snapshot() {
        return new OverlaySettings(preset, refresh, customBase, overrides);
    }

    private void updateEntries() {
        boolean editable = preset == Preset.CUSTOM;
        baseButton.active = editable;
        resetButton.active = editable;
        entries.populate(snapshot(), editable);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        Component hint = preset == Preset.CUSTOM ? text("screen.discovery_hint") : text("screen.custom_hint");
        graphics.drawCenteredString(font, hint, width / 2, 71, 0xC9C9C9);
    }

    private static Component option(String group, Enum<?> value) {
        return text(group + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component text(String label) {
        return Component.translatable("f3ather." + label);
    }

    private final class EntryList extends ContainerObjectSelectionList<Row> {
        private final int rowWidth;

        EntryList(Minecraft minecraft, int width, int height, int rowWidth) {
            super(minecraft, width, height, LIST_TOP, ROW_HEIGHT);
            this.rowWidth = rowWidth - 16;
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        void populate(OverlaySettings settings, boolean editable) {
            double scroll = getScrollAmount();
            clearEntries();
            addEntry(new Row(text("screen.vanilla"), null));
            for (VanillaEntry entry : VanillaEntry.values()) {
                addEntry(row(Component.translatable(entry.translationKey()), entry.id(), entry.minimum(),
                        settings, editable));
            }

            Map<String, String> known = new TreeMap<>(addons.labels());
            overrides.keySet().stream().filter(AddonEntries::isAddonId).forEach(id ->
                    known.putIfAbsent(id, AddonEntries.labelFromId(id)));
            for (boolean left : new boolean[]{true, false}) {
                addEntry(new Row(text(left ? "screen.addons_left" : "screen.addons_right"), null));
                known.forEach((id, label) -> {
                    if (AddonEntries.isLeft(id) == left && !label.isEmpty()) {
                        addEntry(row(Component.literal(label), id, BuiltIn.ORIGINAL, settings, editable));
                    }
                });
                addEntry(row(text("screen.other"), AddonEntries.otherId(left), BuiltIn.ORIGINAL, settings, editable));
            }
            setScrollAmount(scroll);
        }

        private Row row(Component label, String id, BuiltIn minimum, OverlaySettings settings, boolean editable) {
            var toggle = CycleButton.onOffBuilder(settings.shows(id, minimum)).displayOnlyValue()
                    .create(0, 0, 65, 20, label, (button, visible) -> {
                        if (customBase.includes(minimum) == visible) {
                            overrides.remove(id);
                        } else {
                            overrides.put(id, visible);
                        }
                    });
            toggle.active = editable;
            return new Row(label, toggle);
        }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Component label;
        private final AbstractWidget toggle;

        Row(Component label, AbstractWidget toggle) {
            this.label = label;
            this.toggle = toggle;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (toggle == null) {
                graphics.drawString(font, label, left + 3, top + 6, 0xFFD75E);
                return;
            }
            String clipped = font.plainSubstrByWidth(label.getString(), rowWidth - 76);
            if (!clipped.equals(label.getString())) {
                clipped = font.plainSubstrByWidth(clipped, rowWidth - 86) + "…";
            }
            graphics.drawString(font, clipped, left + 3, top + 6, 0xFFFFFF);
            toggle.setPosition(left + rowWidth - toggle.getWidth(), top);
            toggle.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return toggle == null ? List.of() : List.of(toggle);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return toggle == null ? List.of() : List.of(toggle);
        }
    }
}
