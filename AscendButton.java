package de.ascend.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Flacher Button im ASCEND-Look. Funktioniert wie ein normaler ButtonWidget (Maus + Tastatur). */
public class AscendButton extends ButtonWidget {
    private boolean selected;

    public AscendButton(int x, int y, int w, int h, Text label, PressAction onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public AscendButton selected(boolean s) { this.selected = s; return this; }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int bg = !this.active ? Theme.BUTTON_DISABLED
            : selected ? Theme.BUTTON_SELECTED
            : this.isHovered() ? Theme.BUTTON_HOVER : Theme.BUTTON;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ctx.fill(x, y, x + w, y + h, Theme.BORDER_BLUE);          // 1px blauer Rand
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        ctx.drawCenteredTextWithShadow(tr, getMessage(), x + w / 2, y + (h - 8) / 2, Theme.WHITE);
    }
}
