package de.ascend.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

/**
 * Beweist dem Backend die Identität über den Mojang-Sessionserver (wie beim Server-Login):
 * Der Client meldet "joinServer(serverId)", das Backend prüft per hasJoined. Es wird nur ein
 * einmaliges Zufallstoken (serverId) an das Backend gesendet – nie das Access-Token.
 *
 * HINWEIS: Das ist die einzige Stelle, die von internen Minecraft-Klassen abhängt, die sich zwischen
 * Versionen ändern können. Bei einem Compilerfehler nur diese Methode anpassen.
 */
final class MojangAuth {
    private MojangAuth() {}

    /** @return null bei Erfolg, sonst Fehlertext */
    static String join(String serverId) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Session session = mc.getSession();
            if (session.getUuidOrNull() == null) return "Keine gültige Minecraft-Sitzung (Offline-Konto?).";
            mc.getSessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
            return null;
        } catch (Exception e) {
            return "Identitätsprüfung fehlgeschlagen: " + e.getClass().getSimpleName();
        }
    }
}
