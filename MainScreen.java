package de.ascend.client.ui;

import de.ascend.client.ModConfig;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

public final class MainScreen extends AscendScreen {

    public MainScreen() { super("ASCEND", null); }

    @Override protected int panelWidth() { return 300; }
    @Override protected int panelHeight() { return 230; }

    @Override
    protected void build() {
        int x = px + (pw - Theme.BTN_W) / 2;
        int y = py + 44;
        int step = Theme.BTN_H + Theme.GAP + 6;

        addDrawableChild(new AscendButton(x, y, Theme.BTN_W, Theme.BTN_H, Text.literal("ASCEND Discord"), b -> openDiscord()));
        addDrawableChild(new AscendButton(x, y + step, Theme.BTN_W, Theme.BTN_H, Text.literal("Clan Info"),
            b -> this.client.setScreen(new ClanInfoScreen(this))));
        addDrawableChild(new AscendButton(x, y + step * 2, Theme.BTN_W, Theme.BTN_H, Text.literal("ASCEND Clan Livestreams"),
            b -> this.client.setScreen(new StreamScreen(this))));
        addDrawableChild(new AscendButton(x, y + step * 3, Theme.BTN_W, Theme.BTN_H, Text.literal("Auktion"),
            b -> this.client.setScreen(new AuctionScreen(this))));
        addBackButton();
    }

    private void openDiscord() {
        try {
            // Nur bei echtem Klick; Link unverändert
            Util.getOperatingSystem().open(URI.create(ModConfig.DISCORD_URL));
            setStatus("Discord wird im Browser geöffnet …", Theme.OK);
        } catch (Throwable t) {
            setStatus("Browser konnte nicht geöffnet werden. Link: " + ModConfig.DISCORD_URL, Theme.ERROR);
        }
    }
}
