package de.ascend.client.ui;

import de.ascend.client.AscendClient;
import de.ascend.client.logic.AuctionManager;
import de.ascend.client.logic.AuctionManager.Duration;
import de.ascend.client.logic.AuctionManager.State;
import de.ascend.client.logic.Money;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Lokale Auktionsanzeige. Reserviert nichts, synchronisiert nichts, rührt das Item nie an. */
public final class AuctionScreen extends AscendScreen {
    private final AuctionManager auction = AscendClient.AUCTION;

    private TextFieldWidget minBidField;
    private final List<AscendButton> durationButtons = new ArrayList<>();
    private AscendButton startButton, cancelButton;
    private Duration selected = Duration.S30;
    private String savedMinBid = "";
    private int scroll;

    public AuctionScreen(Screen parent) {
        super("Auktion", parent);
        auction.reset();   // Ergebnisse einer beendeten Auktion verschwinden beim erneuten Öffnen
    }

    @Override protected int panelWidth() { return 470; }
    @Override protected int panelHeight() { return 310; }

    @Override
    protected void build() {
        durationButtons.clear();
        minBidField = new TextFieldWidget(this.textRenderer, px + 10, py + 128, 200, 18, Text.literal("Mindestgebot"));
        minBidField.setMaxLength(24);
        minBidField.setPlaceholder(Text.literal("z. B. 1.000.000"));
        minBidField.setText(savedMinBid);
        addDrawableChild(minBidField);

        Duration[] ds = Duration.values();
        for (int i = 0; i < ds.length; i++) {
            Duration d = ds[i];
            int x = px + 10 + (i % 2) * 102;
            int y = py + 166 + (i / 2) * 24;
            AscendButton b = new AscendButton(x, y, 98, 20, Text.literal(d.label), btn -> selected = d);
            durationButtons.add(b);
            addDrawableChild(b);
        }
        startButton = new AscendButton(px + 10, py + 218, 200, 22, Text.literal("Auktion starten"), b -> start());
        cancelButton = new AscendButton(px + 10, py + 244, 200, 20, Text.literal("Auktion abbrechen"), b -> auction.cancel());
        addDrawableChild(startButton);
        addDrawableChild(cancelButton);
        addBackButton();
    }

    @Override
    public void removed() {
        if (minBidField != null) savedMinBid = minBidField.getText();
    }

    private ItemStack held() {
        return this.client.player == null ? ItemStack.EMPTY : this.client.player.getMainHandStack();
    }

    private void start() {
        ItemStack s = held();
        var err = auction.start(s.isEmpty() ? "" : s.getName().getString(), s.getCount(), !s.isEmpty(),
            minBidField.getText(), selected, this.client.getSession().getUsername());
        if (err.isPresent()) setStatus(err.get(), Theme.ERROR);
        else { scroll = 0; setStatus("Auktion gestartet (nur lokale Anzeige).", Theme.OK); }
    }

    @Override
    protected void drawBackdrop(DrawContext ctx, int mouseX, int mouseY) {
        card(ctx, px + 10, py + 36, px + 210, py + 100);          // Item
        card(ctx, px + 220, py + 36, px + pw - 10, py + ph - 36); // Status
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean running = auction.state() == State.RUNNING;
        ItemStack stack = held();

        // Steuerelemente ein-/ausschalten
        minBidField.setEditable(!running);
        for (int i = 0; i < durationButtons.size(); i++) {
            durationButtons.get(i).selected(Duration.values()[i] == selected).active = !running;
        }
        var parsed = Money.parse(minBidField.getText());
        startButton.active = !running && !stack.isEmpty() && parsed.isPresent();
        cancelButton.active = running;

        // Item-Vorschau (nur lesen, nie verändern)
        if (!running) {
            if (stack.isEmpty()) {
                text(ctx, "Kein Item in der Haupthand.", px + 18, py + 60, Theme.WARN);
                text(ctx, "Item in die Hand nehmen.", px + 18, py + 74, Theme.MUTED);
            } else {
                ctx.drawItem(stack, px + 18, py + 48);
                text(ctx, trim(stack.getName().getString(), 160), px + 42, py + 46, Theme.WHITE);
                text(ctx, "Anzahl: " + stack.getCount(), px + 42, py + 60, Theme.MUTED);
            }
        } else {
            text(ctx, trim(auction.itemName(), 180), px + 18, py + 46, Theme.WHITE);
            text(ctx, "Anzahl: " + auction.itemCount(), px + 18, py + 60, Theme.MUTED);
        }

        text(ctx, "Mindestgebot:", px + 10, py + 116, Theme.DARK_TEXT);
        if (minBidField.getText().isBlank()) text(ctx, "Bitte Betrag eingeben.", px + 10, py + 150, 0xFF5A5F66);
        else if (parsed.isPresent()) text(ctx, "Mindestgebot: " + Money.format(parsed.get()), px + 10, py + 150, 0xFF1565C0);
        else text(ctx, "Ungültiger Betrag (nur positive Zahlen).", px + 10, py + 150, 0xFFC62828);
        text(ctx, "Dauer:", px + 10, py + 156 - 0, Theme.DARK_TEXT);

        // Statuskarte
        int x = px + 228;
        int y = py + 42;
        State st = auction.state();
        text(ctx, "Status: " + auction.statusText(), x, y, st == State.RUNNING ? Theme.OK : st == State.IDLE ? Theme.MUTED : Theme.WARN);
        if (st != State.IDLE) {
            text(ctx, "Item: " + trim(auction.itemName() + " x" + auction.itemCount(), pw - 250), x, y + 12, Theme.WHITE);
            text(ctx, "Mindestgebot: " + Money.format(auction.minBid()), x, y + 24, Theme.WHITE);
            if (auction.hasBid()) {
                text(ctx, "Höchste erkannte Zahlung: " + Money.format(auction.highestAmount()), x, y + 36, Theme.WHITE);
                text(ctx, "Von: " + auction.highestBidder() + " (nicht verifiziert)", x, y + 48, Theme.WARN);
            } else {
                text(ctx, st == State.ENDED ? "Kein gültiges Gebot" : "Noch kein gültiges Gebot", x, y + 36, Theme.MUTED);
            }
            if (st == State.RUNNING) {
                ctx.drawText(this.textRenderer, "Verbleibende Zeit: " + auction.remainingFormatted(), x, y + 62, Theme.WHITE, true);
            } else {
                text(ctx, "Verbleibende Zeit: 00:00", x, y + 62, Theme.MUTED);
            }

            text(ctx, "Erkannte Zahlungen:", x, y + 78, Theme.MUTED);
            var pays = auction.payments();
            int rowH = 11;
            int listBottom = py + ph - 52;
            int visible = Math.max(1, (listBottom - (y + 90)) / rowH);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, pays.size() - visible)));
            for (int i = 0; i < visible && i + scroll < pays.size(); i++) {
                var p = pays.get(pays.size() - 1 - (i + scroll));   // neueste zuerst
                int color = p.countedAsBid() ? Theme.OK : Theme.MUTED;
                text(ctx, trim(p.payer() + "  " + Money.format(p.amount()) + (p.countedAsBid() ? "  ✔?" : "  –"), pw - 250), x, y + 90 + i * rowH, color);
            }
            if (pays.isEmpty()) text(ctx, "Noch keine Zahlung erkannt.", x, y + 90, Theme.MUTED);
        }
        text(ctx, "Nur lokale Anzeige – keine Reservierung,", x, py + ph - 48, 0xFFFFC857);
        text(ctx, "kein Abgleich mit anderen Spielern.", x, py + ph - 38, 0xFFFFC857);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        return true;
    }
}
