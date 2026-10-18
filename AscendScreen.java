package de.ascend.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Basis aller ASCEND-Bildschirme: hellgrauer Innenbereich, deutlich blauer Rahmen, dunkler Titelbalken.
 * ESC (bzw. close()) führt zurück zum übergeordneten Screen oder schließt das Menü.
 * Die Größe passt sich der Fenstergröße an.
 */
public abstract class AscendScreen extends Screen {
    protected final Screen parent;
    protected int px, py, pw, ph;           // Panel
    protected String status = "";
    protected int statusColor = Theme.MUTED;

    protected AscendScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.parent = parent;
    }

    protected int panelWidth() { return 440; }
    protected int panelHeight() { return 280; }

    @Override
    protected final void init() {
        pw = Math.min(panelWidth(), this.width - 16);
        ph = Math.min(panelHeight(), this.height - 16);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        build();
    }

    /** Widgets erzeugen (px/py/pw/ph sind gesetzt). */
    protected abstract void build();

    /** Hintergrund-Elemente (Karten), die UNTER den Widgets liegen müssen. */
    protected void drawBackdrop(DrawContext ctx, int mouseX, int mouseY) {}

    /** Texte/Items, die ÜBER den Widgets liegen. */
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Theme.DIM);
        // Blauer Rahmen (4 px) um das gesamte Fenster
        ctx.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, Theme.BORDER_BLUE);
        ctx.fill(px, py, px + pw, py + ph, Theme.PANEL_BG);
        // Titelbalken
        ctx.fill(px, py, px + pw, py + 26, Theme.HEADER_BG);
        ctx.fill(px, py + 26, px + pw, py + 28, Theme.BORDER_BLUE);
        drawBackdrop(ctx, mouseX, mouseY);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, px + pw / 2, py + 9, Theme.WHITE);
        drawForeground(ctx, mouseX, mouseY, delta);
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, trim(status, pw - 120), px + 100, py + ph - 20, statusColor);
        }
    }

    protected String trim(String s, int maxPx) {
        if (this.textRenderer.getWidth(s) <= maxPx) return s;
        String t = s;
        while (t.length() > 1 && this.textRenderer.getWidth(t + "…") > maxPx) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    protected void setStatus(String text, int color) { this.status = text; this.statusColor = color; }

    protected void addBackButton() {
        addDrawableChild(new AscendButton(px + 10, py + ph - 28, 80, 20,
            Text.literal(parent == null ? "Schließen" : "Zurück"), b -> close()));
    }

    /** Weißer Text auf Karten (dunkel). */
    protected void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(this.textRenderer, s, x, y, color);
    }

    protected void card(DrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.fill(x1, y1, x2, y2, Theme.CARD_BG);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }   // Spiel läuft weiter (Timer, Server)
}
