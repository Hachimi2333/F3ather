package top.hachimi2333.config;

import com.nstut.openui.api.Divider;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.IconWidget;
import com.nstut.openui.controls.Popover;
import com.nstut.openui.controls.ScrollView;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.function.Consumer;

import static top.hachimi2333.config.SettingsUi.*;

public final class F3atherScreen extends UiScreen {
    private static final int HEADER = 26;
    private final Screen parent;
    private ScrollView tabs;
    private double scroll;
    private OverlayHandle refreshMenu;
    private OverlayHandle nameDialog;
    private Runnable submitName;

    public F3atherScreen(Screen parent) {
        super(label("title"));
        this.parent = parent;
    }

    @Override protected int uiLeft() { return (width - uiWidth()) / 2; }
    @Override protected int uiTop() { return (height - uiHeight()) / 2; }
    @Override protected int uiWidth() { return Math.max(1, Math.min(500, width - 16)); }
    @Override protected int uiHeight() { return Math.max(1, Math.min(294, height - 16)); }

    private int padding() { return uiWidth() < 340 ? 7 : 12; }
    private int gutter() { return uiWidth() < 340 ? 7 : 13; }
    private int preferencesWidth() { return Math.min(150, (uiWidth() - 2 * padding()) / 3); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int left = uiLeft();
        int top = uiTop();
        int pad = padding();
        int dividerX = left + pad + preferencesWidth() + gutter();
        if (minecraft.level == null) renderPanorama(graphics, delta);
        graphics.flush();
        renderBlurredBackground(delta);
        graphics.fill(0, 0, width, height, 0x4017181a);
        graphics.fill(left, top, left + uiWidth(), top + uiHeight(), SURFACE);
        graphics.renderOutline(left, top, uiWidth(), uiHeight(), DIVIDER);
        graphics.fill(dividerX, top + pad + HEADER + 8, dividerX + 1, top + uiHeight() - pad, DIVIDER);
    }

    @Override
    protected UIComponent buildUI() {
        uiRuntime().theme(THEME);
        var settings = ClientConfig.current();
        var title = Ui.row(IconWidget.texture("f3ather", "textures/gui/logo.png", 16, 16).size(16),
                Ui.text(Component.literal("F3ather")).style(TextStyle.TITLE.withColor(TEXT)).flex(),
                button(label("back"), this::onClose))
                .gap(6).align(Alignment.CENTER).fillWidth().height(HEADER);
        var preferences = Ui.column(
                Ui.text(label("general")).style(TextStyle.HEADING.withColor(TEXT)).height(23),
                Ui.text(label("refresh")).color(TEXT).ellipsis().height(17),
                refreshRate(settings.refreshHz()),
                preference("hide_tab_bar", settings.hideTabBar(),
                        value -> ClientConfig.save(ClientConfig.current().withHideTabBar(value))),
                preference("compatibility", settings.compatibility(),
                        value -> ClientConfig.save(ClientConfig.current().withCompatibility(value))),
                preference("disable_animations", settings.disableAnimations(),
                        value -> ClientConfig.save(ClientConfig.current().withDisableAnimations(value))))
                .gap(3).width(preferencesWidth());

        var tabsHeading = Ui.row(Ui.text(label("tabs")).style(TextStyle.HEADING.withColor(TEXT)).flex(),
                button(label("new_tab"), () -> editName(null)).filled())
                .align(Alignment.CENTER).height(23).fillWidth();
        var rows = Ui.column().fillWidth();
        for (BuiltIn tab : BuiltIn.values()) {
            rows.addChild(tabRow(Component.translatable("f3ather.tab." + tab.id()),
                    settings.enabled().contains(tab), value -> ClientConfig.save(ClientConfig.current().withBuiltIn(tab, value)), null));
        }
        for (Tab tab : settings.customTabs()) {
            rows.addChild(tabRow(Component.literal(tab.name()), tab.enabled(), value -> changeTab(tab.id(), current -> current.toggle(value)), tab));
        }
        tabs = Ui.scroll(rows).scrollTo(scroll);
        var tabColumn = Ui.column(tabsHeading, tabs.fillWidth().flex()).gap(4).flex().fillHeight();
        var columns = Ui.row(preferences, Ui.spacer().width(1), tabColumn)
                .gap(gutter()).fillWidth().flex();
        return Ui.padding(padding(), Ui.column(title, new Divider(DIVIDER).fillWidth(), columns)
                .gap(4).fillWidth().fillHeight()).fillWidth().fillHeight();
    }

    private UIComponent tabRow(Component name, boolean enabled, Consumer<Boolean> onToggle, Tab tab) {
        var row = Ui.row(Ui.text(name).color(TEXT).ellipsis().flex()).align(Alignment.CENTER).gap(3).fillWidth();
        if (tab != null) {
            row.addChild(icon(label("edit"), Icon.EDIT, () -> openEditor(tab.id())));
            row.addChild(icon(label("rename"), Icon.RENAME, () -> editName(tab.id())));
            row.addChild(icon(label("delete"), Icon.DELETE, () -> delete(tab)).destructive());
        }
        row.addChild(toggle(name, enabled, onToggle));
        return Ui.column(row.height(25), new Divider(DIVIDER).fillWidth()).fillWidth();
    }

    private static UIComponent preference(String key, boolean enabled, Consumer<Boolean> onToggle) {
        var title = label(key);
        return Ui.row(Ui.text(title).color(TEXT).wrap().maxLines(2).ellipsis().flex(), toggle(title, enabled, onToggle))
                .gap(4).align(Alignment.CENTER).height(24).fillWidth();
    }

    private UIComponent refreshRate(int hz) {
        var anchor = dropdown(rateLabel(hz));
        anchor.fillWidth();
        anchor.onPress(() -> {
            if (refreshMenu != null && refreshMenu.isOpen()) {
                refreshMenu.close();
                return;
            }
            var list = Ui.column().fillWidth();
            Button selected = null;
            int current = ClientConfig.current().refreshHz();
            for (int preset : OverlaySettings.REFRESH_PRESETS) {
                var option = option(rateLabel(preset), () -> {
                    ClientConfig.save(ClientConfig.current().withRefresh(preset));
                    anchor.setLabel(rateLabel(preset));
                    refreshMenu.close();
                    anchor.requestFocus();
                });
                option.fillWidth();
                if (preset == current) {
                    option.selected(true);
                    selected = option;
                }
                list.addChild(option);
            }
            var menu = new Popover(anchor, list) {
                @Override
                public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float delta) {
                    if (!visible) return;
                    outline(graphics, x, y, width, height, SURFACE, DIVIDER);
                    renderChildren(graphics, font, mouseX, mouseY, delta);
                }
            };
            refreshMenu = menu.matchAnchorWidth().offset(2).show(uiRuntime().overlays());
            if (selected != null) uiRuntime().scheduleFrame(selected, selected::requestFocus);
        });
        return anchor;
    }

    private static Component rateLabel(int hz) {
        return hz == 0 ? label("unlimited") : label("hz", hz);
    }

    private void editName(String id) {
        Tab existing = id == null ? null : ClientConfig.current().customTab(id);
        if (id != null && existing == null) return;
        var title = label(existing == null ? "new_tab" : "rename");
        var name = Signals.of(existing == null ? "" : existing.name());
        var input = new TextInput(name, font, label("tab_name"), 40);
        submitName = () -> {
            String cleaned = name.get().strip();
            if (cleaned.isEmpty() || nameDialog == null || !nameDialog.isOpen()) return;
            nameDialog.close();
            if (existing == null) {
                Tab created = Tab.create(cleaned);
                ClientConfig.save(ClientConfig.current().withTab(created));
                openEditor(created.id());
            } else {
                changeTab(id, tab -> tab.rename(cleaned));
                refresh();
            }
        };
        var save = button(label(existing == null ? "create" : "save"), () -> submitName.run())
                .filled().enabled(!name.get().isBlank());
        name.subscribe(value -> save.enabled(!value.isBlank()));
        var actions = Ui.row(Ui.spacer().flex(), button(label("cancel"), () -> nameDialog.close()), save)
                .gap(5).align(Alignment.CENTER).fillWidth();
        nameDialog = Dialog.show(uiRuntime().overlays(), panel(Ui.column(Ui.heading(title),
                        input.fillWidth(), actions).gap(8).fillWidth(), 12)
                .width(Math.min(246, width - 12)));
        uiRuntime().scheduleFrame(input, input::requestFocus);
    }

    private void delete(Tab tab) {
        final OverlayHandle[] dialog = new OverlayHandle[1];
        var actions = Ui.row(Ui.spacer().flex(),
                button(label("cancel"), () -> dialog[0].close()),
                button(label("delete"), () -> {
                    dialog[0].close();
                    ClientConfig.save(ClientConfig.current().withoutTab(tab.id()));
                    refresh();
                }).filled().destructive()).gap(5).fillWidth();
        dialog[0] = Dialog.show(uiRuntime().overlays(),
                panel(Ui.column(Ui.heading(Component.literal(tab.name())).ellipsis(), actions).gap(8), 12)
                        .width(Math.min(236, width - 12)));
    }

    private void changeTab(String id, java.util.function.UnaryOperator<Tab> change) {
        var settings = ClientConfig.current();
        var tab = settings.customTab(id);
        if (tab != null) ClientConfig.save(settings.withTab(change.apply(tab)));
    }

    private void openEditor(String id) {
        rememberScroll();
        minecraft.setScreen(new TabEditorScreen(this, id));
    }

    private void rememberScroll() {
        if (tabs != null) scroll = tabs.scrollOffset();
    }

    private void refresh() {
        rememberScroll();
        rebuildUI();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameDialog != null && nameDialog.isOpen() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            submitName.run();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    static Component label(String key, Object... arguments) {
        return Component.translatable("f3ather.screen." + key, arguments);
    }
}
