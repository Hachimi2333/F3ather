package top.hachimi2333.config;

import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.ScrollView;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.runtime.UiRuntime;
import com.nstut.openui.state.Signals;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.overlay.OverlaySettings.Tab;
import top.hachimi2333.overlay.VanillaEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static top.hachimi2333.config.F3atherScreen.label;
import static top.hachimi2333.config.SettingsUi.*;

final class EntrySearch {
    private final List<Choice> choices = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final UIComponent entries = Ui.column().gap(2).fillWidth();
    private final ScrollView scroll = Ui.scroll(entries);
    private final Consumer<String> add;
    private final OverlayHandle overlay;
    private List<Choice> results = List.of();
    private int selected;

    EntrySearch(UiRuntime runtime, Font font, int width, int height, Tab tab, Consumer<String> add) {
        this.add = add;
        for (VanillaEntry entry : VanillaEntry.values()) {
            if (tab.contains(entry.id())) continue;
            var examples = F3ather.HUD.previewLines(entry.id(), false);
            String alias = entry.name().replace('_', ' ') + " " + String.join(" ", examples);
            choices.add(new Choice(entry.id(), Component.translatable(entry.translationKey()).getString(), "", alias));
        }
        for (var entry : F3ather.ADDONS.entries()) {
            if (tab.contains(entry.id())) continue;
            choices.add(new Choice(entry.id(), entry.title(), entry.source().name(), entry.source().id() + " " + entry.sample()));
        }

        var query = Signals.of("");
        var field = new TextInput(query, font, label("search"), 80);
        var content = Ui.column(field.fillWidth(),
                scroll.fillWidth().height(Math.clamp(height - 76, 24, 140))).gap(6).fillWidth();
        var card = panel(content, 10).width(Math.min(342, width - 14));
        filter("");
        query.subscribe(this::filter);
        overlay = Dialog.show(runtime.overlays(), card);
        runtime.scheduleFrame(field, field::requestFocus);
    }

    boolean open() { return overlay.isOpen(); }

    boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (!results.isEmpty()) {
                selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, results.size() - 1);
                highlight();
                scroll.reveal(buttons.get(selected));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!results.isEmpty()) choose(results.get(selected));
            return true;
        }
        return false;
    }

    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        results = choices.stream().filter(choice -> choice.matches(needle)).toList();
        selected = 0;
        entries.clearChildren();
        buttons.clear();
        for (Choice choice : results) {
            var button = searchResult(choice.title(), choice.owner(), () -> choose(choice));
            buttons.add(button);
            entries.addChild(button.fillWidth());
        }
        if (buttons.isEmpty()) entries.addChild(Ui.text(label("no_results")).color(MUTED).centered().fillWidth());
        highlight();
        scroll.scrollTo(0);
    }

    private void highlight() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).selected(i == selected);
        }
    }

    private void choose(Choice choice) {
        overlay.close();
        add.accept(choice.id());
    }

    private record Choice(String id, String title, String owner, String alias) {
        boolean matches(String query) {
            return (title + " " + owner + " " + alias).toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
