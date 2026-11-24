package top.hachimi2333.config;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UiRender;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.ScrollView;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.theme.TextStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.function.Consumer;

public final class F3atherScreen extends UiScreen {
    static final int ACCENT = 0xff9ce2d8;
    static final int TEXT = 0xffe8eff3;
    static final int MUTED = 0xffaab7c1;
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath("f3ather", "textures/gui/logo.png");
    private final Screen parent;
    private ScrollView tabs;
    private double scroll;
    private Signal<Double> rate;
    private int savedRate;
    private OverlayHandle nameDialog;
    private Runnable submitName;

    public F3atherScreen(Screen parent) {
        super(label("title"));
        this.parent = parent;
    }

    @Override protected int uiLeft() { return (width - uiWidth()) / 2; }
    @Override protected int uiTop() { return (height - uiHeight()) / 2; }
    @Override protected int uiWidth() { return Math.max(1, Math.min(444, width - 16)); }
    @Override protected int uiHeight() { return Math.max(1, Math.min(320, height - 16)); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xff151d25, 0xff0e131a);
        UiRender.roundedRect(graphics, uiLeft(), uiTop(), uiWidth(), uiHeight(), 10, 0xff2b3843);
        UiRender.roundedRect(graphics, uiLeft() + 1, uiTop() + 1, uiWidth() - 2, uiHeight() - 2, 9, 0xff19222b);
        graphics.blit(LOGO, uiLeft() + 13, uiTop() + 13, 0, 0, 16, 16, 16, 16);
    }

    @Override
    protected UIComponent buildUI() {
        var settings = ClientConfig.current();
        savedRate = settings.refreshHz();
        rate = Signals.of(savedRate == 0 ? 61.0 : (double) savedRate);
        var title = Ui.row(Ui.spacer().width(21),
                Ui.text(Component.literal("F3ather")).style(TextStyle.TITLE.withColor(TEXT)).flex(),
                Ui.button(label("back"), this::onClose).small().ghost())
                .align(Alignment.CENTER).fillWidth().height(23);
        var refresh = Ui.row(Ui.text(label("refresh")).color(TEXT).width(91),
                Ui.slider(rate, 1, 61).flex(),
                Ui.text(() -> sliderHz() == 0 ? label("every_frame") : label("hz", sliderHz()))
                        .color(ACCENT).centered().width(66))
                .gap(5).align(Alignment.CENTER).height(24).fillWidth();
        var compatibility = Ui.row(Ui.text(label("compatibility")).color(TEXT).flex(),
                toggle(settings.compatibility(), value -> ClientConfig.save(ClientConfig.current().withCompatibility(value))))
                .gap(5).align(Alignment.CENTER).height(22).fillWidth();
        var preferences = Ui.card(refresh, compatibility).padding(8).outlined(true).fillWidth();

        var tabsHeading = Ui.row(Ui.text(label("tabs")).style(TextStyle.HEADING.withColor(TEXT)).flex(),
                Ui.button(label("new_tab"), () -> editName(null)).small().primary())
                .align(Alignment.CENTER).height(21).fillWidth();
        var rows = Ui.column().gap(3).fillWidth();
        for (BuiltIn tab : BuiltIn.values()) {
            rows.addChild(tabRow(Component.translatable("f3ather.tab." + tab.id()),
                    settings.enabled().contains(tab), value -> ClientConfig.save(ClientConfig.current().withBuiltIn(tab, value)), null));
        }
        for (Tab tab : settings.customTabs()) {
            rows.addChild(tabRow(Component.literal(tab.name()), tab.enabled(), value -> changeTab(tab.id(), current -> current.toggle(value)), tab));
        }
        tabs = Ui.scroll(rows).scrollTo(scroll);
        return Ui.padding(12, Ui.column(title, preferences, tabsHeading, tabs.fillWidth().flex())
                .gap(7).fillWidth().fillHeight()).fillWidth().fillHeight();
    }

    private UIComponent tabRow(Component name, boolean enabled, Consumer<Boolean> onToggle, Tab tab) {
        var row = Ui.row(Ui.text(name).color(TEXT).ellipsis().flex()).align(Alignment.CENTER).gap(5).fillWidth();
        if (tab != null) {
            row.addChild(Ui.button(label("edit"), () -> openEditor(tab.id())).small().secondary());
            row.addChild(Ui.button(label("rename"), () -> editName(tab.id())).small().ghost());
            row.addChild(Ui.button(Component.literal("×"), () -> delete(tab)).small().ghost()
                    .width(18).tooltip(label("delete")));
        }
        row.addChild(toggle(enabled, onToggle));
        return Ui.card(row.height(17)).padding(4).outlined(true).fillWidth();
    }

    private static UIComponent toggle(boolean startingValue, Consumer<Boolean> onChange) {
        var state = Signals.of(startingValue);
        state.subscribe(onChange);
        return Ui.toggle(state).width(24);
    }

    private void editName(String id) {
        Tab existing = id == null ? null : ClientConfig.current().customTab(id);
        if (id != null && existing == null) return;
        var title = label(existing == null ? "new_tab" : "rename");
        var name = Signals.of(existing == null ? "" : existing.name());
        var input = Ui.textField(name, font).placeholder(label("tab_name").getString()).maxLength(40);
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
        var save = Ui.button(label(existing == null ? "create" : "save"), () -> submitName.run())
                .small().primary().enabled(!name.get().isBlank());
        name.subscribe(value -> save.enabled(!value.isBlank()));
        var actions = Ui.row(Ui.spacer().flex(), Ui.button(label("cancel"), () -> nameDialog.close()).small().ghost(), save)
                .gap(5).align(Alignment.CENTER).fillWidth();
        nameDialog = Dialog.show(uiRuntime().overlays(), Ui.card(Ui.column(Ui.heading(title),
                        input.fillWidth(), actions).gap(8).fillWidth())
                .padding(12).elevated(true).outlined(true).width(Math.min(246, width - 12)));
        uiRuntime().scheduleFrame(input, input::requestFocus);
    }

    private void delete(Tab tab) {
        final OverlayHandle[] dialog = new OverlayHandle[1];
        var actions = Ui.row(Ui.spacer().flex(),
                Ui.button(label("cancel"), () -> dialog[0].close()).small().ghost(),
                Ui.button(label("delete"), () -> {
                    dialog[0].close();
                    ClientConfig.save(ClientConfig.current().withoutTab(tab.id()));
                    refresh();
                }).small().danger()).gap(5).fillWidth();
        dialog[0] = Dialog.show(uiRuntime().overlays(),
                Ui.card(Ui.column(Ui.heading(Component.literal(tab.name())).ellipsis(), actions).gap(8))
                        .padding(12).elevated(true).outlined(true).width(Math.min(236, width - 12)));
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

    private int sliderHz() {
        int value = (int) Math.round(rate.get());
        return value == 61 ? 0 : value;
    }

    private void saveRate() {
        if (rate == null) return;
        int value = sliderHz();
        if (value != savedRate) {
            ClientConfig.save(ClientConfig.current().withRefresh(value));
            savedRate = value;
        }
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = super.mouseReleased(x, y, button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) saveRate();
        return handled;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameDialog != null && nameDialog.isOpen() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            submitName.run();
            return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) saveRate();
        return handled;
    }

    @Override
    public void removed() {
        saveRate();
        super.removed();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    static Component label(String key, Object... arguments) {
        return Component.translatable("f3ather.screen." + key, arguments);
    }
}
