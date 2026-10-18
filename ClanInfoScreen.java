package de.ascend.client.ui;

import de.ascend.client.AscendClient;
import de.ascend.client.logic.ClanInfoCollector;
import de.ascend.client.logic.ClanInfoParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClanInfoScreen extends AscendScreen {
    private int scroll;
    private int listTop, listBottom;

    public ClanInfoScreen(Screen parent) { super("Clan Info", parent); }

    @Override protected int panelWidth() { return 400; }
    @Override protected int panelHeight() { return 280; }

    @Override
    protected void build() {
        addBackButton();
        addDrawableChild(new AscendButton(px + pw - 110, py + ph - 28, 100, 20, Text.literal("Aktualisieren"), b -> refresh()));
        // Beim bewussten Öffnen einmal anfragen
        if (AscendClient.CLAN.status() == ClanInfoCollector.Status.IDLE || AscendClient.CLAN.info() == null) refresh();
        else if (AscendClient.CLAN.isStale()) setStatus("Angezeigte Daten sind veraltet – auf Aktualisieren klicken.", Theme.WARN);
    }

    private void refresh() {
        scroll = 0;
        if (!AscendClient.requestClanInfo()) setStatus("Nicht im Spiel – Befehl konnte nicht gesendet werden.", Theme.ERROR);
    }

    private List<String> memberLines() {
        List<String> out = new ArrayList<>();
        var info = AscendClient.CLAN.info();
        if (info == null) return out;
        for (Map.Entry<String, String> e : info.memberRoles().entrySet()) out.add(e.getKey() + "  –  " + e.getValue());
        return out;
    }

    @Override
    protected void drawBackdrop(DrawContext ctx, int mouseX, int mouseY) {
        card(ctx, px + 10, py + 36, px + pw - 10, py + 94);
        listTop = py + 108;
        listBottom = py + ph - 36;
        card(ctx, px + 10, listTop, px + pw - 10, listBottom);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var c = AscendClient.CLAN;
        int x = px + 18;
        switch (c.status()) {
            case LOADING -> {
                text(ctx, "Lade Clan-Info … (Antwort von /clan info)", x, py + 44, Theme.WARN);
                setStatus("Warte auf Serverantwort", Theme.WARN);
            }
            case ERROR -> {
                text(ctx, trim(c.error(), pw - 40), x, py + 44, Theme.ERROR);
                setStatus("Fehler – ggf. erneut aktualisieren", Theme.ERROR);
            }
            default -> {}
        }
        var info = c.info();
        if (info != null && c.status() != ClanInfoCollector.Status.LOADING) {
            boolean stale = c.isStale();
            text(ctx, "Mitglieder: " + val(info.members()), x, py + 44, Theme.WHITE);
            text(ctx, "Moderatoren: " + val(info.moderators()), x, py + 58, Theme.WHITE);
            text(ctx, "Co-Owner: " + val(info.coOwners()), x, py + 72, Theme.WHITE);
            String age = "Stand: vor " + c.ageSeconds() + " s" + (stale ? "  (VERALTET)" : "");
            text(ctx, age, x, py + 84, stale ? Theme.WARN : Theme.MUTED);
        } else if (info == null && c.status() != ClanInfoCollector.Status.LOADING && c.status() != ClanInfoCollector.Status.ERROR) {
            text(ctx, "Noch keine Daten.", x, py + 44, Theme.MUTED);
        }

        // Mitgliederliste (scrollbar) – nur was der Server tatsächlich geliefert hat
        List<String> lines = memberLines();
        text(ctx, "Mitglieder & Rollen", x, listTop - 11, Theme.DARK_TEXT);
        int rowH = 11;
        int visible = Math.max(1, (listBottom - listTop - 8) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        if (lines.isEmpty()) {
            text(ctx, "Keine Mitgliederliste in der Serverantwort erkannt.", x, listTop + 6, Theme.MUTED);
        }
        ctx.enableScissor(px + 10, listTop, px + pw - 10, listBottom);
        for (int i = 0; i < visible && i + scroll < lines.size(); i++) {
            text(ctx, lines.get(i + scroll), x, listTop + 5 + i * rowH, Theme.WHITE);
        }
        ctx.disableScissor();
        if (lines.size() > visible) {
            int trackH = listBottom - listTop - 4;
            int barH = Math.max(12, trackH * visible / lines.size());
            int barY = listTop + 2 + (trackH - barH) * scroll / Math.max(1, lines.size() - visible);
            ctx.fill(px + pw - 16, barY, px + pw - 13, barY + barH, Theme.BORDER_BLUE);
        }
    }

    private static String val(Integer v) { return v == null ? "nicht erkannt" : v.toString(); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        return true;
    }
}
