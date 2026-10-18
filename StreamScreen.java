package de.ascend.client.ui;

import de.ascend.client.AscendClient;
import de.ascend.client.StreamService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class StreamScreen extends AscendScreen {
    private static final int ROW_H = 24;

    private TextFieldWidget nameField, linkField;
    private final List<StreamService.Entry> entries = new ArrayList<>();
    private final List<AscendButton> openButtons = new ArrayList<>();
    private final List<AscendButton> removeButtons = new ArrayList<>();
    private int scroll;
    private int listTop, listBottom;
    private boolean loading;
    private String savedName = "", savedLink = "";

    public StreamScreen(Screen parent) { super("ASCEND Clan Livestreams", parent); }

    @Override protected int panelWidth() { return 470; }
    @Override protected int panelHeight() { return 300; }

    @Override
    protected void build() {
        int addW = 112;
        int addX = px + pw - addW - 10;
        int linkX = px + 118;
        nameField = new TextFieldWidget(this.textRenderer, px + 10, py + 50, 100, 18, Text.literal("Spielername"));
        nameField.setMaxLength(16);
        nameField.setPlaceholder(Text.literal("Spielername"));
        nameField.setText(savedName.isEmpty() ? this.client.getSession().getUsername() : savedName);
        linkField = new TextFieldWidget(this.textRenderer, linkX, py + 50, addX - linkX - 6, 18, Text.literal("Livestream-Link"));
        linkField.setMaxLength(200);
        linkField.setPlaceholder(Text.literal("https://twitch.tv/… oder YouTube"));
        linkField.setText(savedLink);
        addDrawableChild(nameField);
        addDrawableChild(linkField);
        addDrawableChild(new AscendButton(addX, py + 49, addW, 20, Text.literal("Stream hinzufügen"), b -> addStream()));

        listTop = py + 76;
        listBottom = py + ph - 36;

        addBackButton();
        addDrawableChild(new AscendButton(px + pw - 110, py + ph - 28, 100, 20, Text.literal("Aktualisieren"), b -> reload()));
        rebuildRows();
        if (entries.isEmpty() && !loading) reload();
    }

    @Override
    public void removed() {
        if (nameField != null) { savedName = nameField.getText(); savedLink = linkField.getText(); }
    }

    private void reload() {
        loading = true;
        setStatus("Lade Streams …", Theme.WARN);
        AscendClient.streams.list((list, err) -> {
            loading = false;
            if (err != null) { setStatus(err, Theme.ERROR); return; }
            entries.clear();
            entries.addAll(list);
            scroll = 0;
            rebuildRows();
            setStatus(list.isEmpty() ? "Noch keine Streams eingetragen." : list.size() + " Stream(s) geladen.", Theme.OK);
        });
    }

    private void addStream() {
        String name = nameField.getText().strip();
        String link = linkField.getText().strip();
        setStatus("Wird gespeichert …", Theme.WARN);
        AscendClient.streams.add(name, link, (v, err) -> {
            if (err != null) { setStatus(err, Theme.ERROR); return; }
            linkField.setText("");
            setStatus("Stream gespeichert.", Theme.OK);
            reload();
        });
    }

    private void rebuildRows() {
        for (var b : openButtons) remove(b);
        for (var b : removeButtons) remove(b);
        openButtons.clear();
        removeButtons.clear();
        String own = this.client.getSession().getUsername();
        for (StreamService.Entry e : entries) {
            AscendButton open = new AscendButton(0, 0, 58, 18, Text.literal("Öffnen"), b -> openStream(e));
            AscendButton rem = new AscendButton(0, 0, 66, 18, Text.literal("Entfernen"), b -> removeStream(e));
            rem.active = !AscendClient.streams.isRemote() || e.player().equalsIgnoreCase(own);
            addDrawableChild(open);
            addDrawableChild(rem);
            openButtons.add(open);
            removeButtons.add(rem);
        }
        layoutRows();
    }

    private void layoutRows() {
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        for (int i = 0; i < entries.size(); i++) {
            int rel = i - scroll;
            boolean show = rel >= 0 && rel < visible;
            int y = listTop + 4 + rel * ROW_H + 3;
            var open = openButtons.get(i);
            var rem = removeButtons.get(i);
            open.visible = show;
            rem.visible = show;
            open.setX(px + pw - 10 - 66 - 4 - 58 - 8);
            rem.setX(px + pw - 10 - 66 - 4);
            open.setY(y);
            rem.setY(y);
        }
    }

    private int visibleRows() { return Math.max(1, (listBottom - listTop - 8) / ROW_H); }

    private void openStream(StreamService.Entry e) {
        try {
            Util.getOperatingSystem().open(URI.create(e.url()));
            setStatus("Stream wird im Browser geöffnet …", Theme.OK);
        } catch (Throwable t) {
            setStatus("Browser konnte nicht geöffnet werden: " + e.url(), Theme.ERROR);
        }
    }

    private void removeStream(StreamService.Entry e) {
        AscendClient.streams.remove(e.player(), (v, err) -> {
            if (err != null) { setStatus(err, Theme.ERROR); return; }
            setStatus("Eintrag entfernt.", Theme.OK);
            reload();
        });
    }

    @Override
    protected void drawBackdrop(DrawContext ctx, int mouseX, int mouseY) {
        card(ctx, px + 10, listTop, px + pw - 10, listBottom);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean remote = AscendClient.streams.isRemote();
        text(ctx, trim(AscendClient.streams.modeLabel(), pw - 20), px + 10, py + 34, remote ? Theme.OK : 0xFF9A5B00);
        layoutRows();
        ctx.enableScissor(px + 10, listTop, px + pw - 10, listBottom);
        int visible = visibleRows();
        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            var e = entries.get(i + scroll);
            int y = listTop + 4 + i * ROW_H + 7;
            text(ctx, e.player(), px + 18, y, Theme.WHITE);
            text(ctx, e.platform(), px + 130, y, Theme.MUTED);
        }
        ctx.disableScissor();
        if (entries.isEmpty()) {
            text(ctx, loading ? "Lade …" : "Keine Streams vorhanden.", px + 18, listTop + 10, Theme.MUTED);
        }
        if (entries.size() > visible) {
            int trackH = listBottom - listTop - 4;
            int barH = Math.max(12, trackH * visible / entries.size());
            int barY = listTop + 2 + (trackH - barH) * scroll / Math.max(1, entries.size() - visible);
            ctx.fill(px + pw - 14, barY, px + pw - 11, barY + barH, Theme.BORDER_BLUE);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        layoutRows();
        return true;
    }
}
