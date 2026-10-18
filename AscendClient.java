package de.ascend.client;

import de.ascend.client.logic.AuctionManager;
import de.ascend.client.logic.ClanInfoCollector;
import de.ascend.client.logic.HugoPaymentParser;
import de.ascend.client.ui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AscendClient implements ClientModInitializer {
    public static final String MOD_ID = "ascendclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModConfig config;
    public static StreamService streams;
    public static final AuctionManager AUCTION = new AuctionManager();
    public static final ClanInfoCollector CLAN = new ClanInfoCollector();

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        config = ModConfig.load();
        streams = new StreamService(config);

        // Eigene Kategorie (ab 1.21.9 ist KeyBinding.Category Pflicht)
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.ascendclient.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                while (openKey.wasPressed()) {
                    // Nur wenn kein Screen offen ist -> nie mehrfach übereinander
                    if (client.currentScreen == null && client.player != null) {
                        client.setScreen(new MainScreen());
                    }
                }
                AUCTION.tick();
                CLAN.tick();
            } catch (Throwable t) {
                LOGGER.warn("Fehler im Tick-Handler", t);
            }
        });

        // Server-Systemnachrichten (keine Spieler-Chats). overlay = Actionbar -> ignorieren.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            try {
                String plain = message.getString();
                CLAN.onServerLine(plain);
                HugoPaymentParser.parse(plain).ifPresent(AUCTION::onPayment);
            } catch (Throwable t) {
                LOGGER.warn("Unerwartete Chatnachricht konnte nicht verarbeitet werden", t);
            }
        });
    }

    /** Sendet "/clan info" – ausschließlich bei bewusster Aktion des Spielers. */
    public static boolean requestClanInfo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return false;
        CLAN.begin();
        mc.player.networkHandler.sendChatCommand("clan info");
        return true;
    }
}
