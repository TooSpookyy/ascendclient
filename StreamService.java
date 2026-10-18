package de.ascend.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.ascend.client.logic.StreamValidator;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Livestream-Liste. Ohne konfigurierte API-Adresse: NUR lokale Liste (nicht mit anderen synchronisiert).
 * Mit API-Adresse: gemeinsame Liste über das Backend (siehe Ordner backend/). Identität wird über die
 * Minecraft-Sitzung (Mojang session server) bewiesen, es werden keine Geheimnisse im JAR gespeichert.
 * Alle Netzwerkzugriffe laufen asynchron, Rückmeldungen auf dem Client-Thread.
 */
public final class StreamService {

    public record Entry(String player, String platform, String url) {}

    public interface Callback<T> { void done(T value, String error); }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ModConfig config;

    public StreamService(ModConfig config) { this.config = config; }

    public boolean isRemote() { return !config.apiUrl.isBlank(); }

    public String modeLabel() {
        return isRemote() ? "Gemeinsame Liste (Backend aktiv)" : "NUR LOKAL – wird NICHT mit anderen Spielern synchronisiert";
    }

    private static <T> void deliver(Callback<T> cb, T value, String error) {
        MinecraftClient.getInstance().execute(() -> cb.done(value, error));
    }

    private String base() {
        String b = config.apiUrl.strip();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private String apiProblem() {
        if (!isRemote()) return null;
        if (!config.apiUrl.strip().toLowerCase().startsWith("https://")) return "API-Adresse muss mit https:// beginnen.";
        return null;
    }

    // ---------------------------------------------------------------- Liste
    public void list(Callback<List<Entry>> cb) {
        if (!isRemote()) {
            List<Entry> out = new ArrayList<>();
            for (ModConfig.LocalStream s : config.localStreams) out.add(new Entry(s.player, s.platform, s.url));
            deliver(cb, out, null);
            return;
        }
        String problem = apiProblem();
        if (problem != null) { deliver(cb, null, problem); return; }
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base() + "/streams"))
                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) { deliver(cb, null, "Server antwortete mit Status " + r.statusCode()); return; }
                JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
                List<Entry> out = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    String url = o.get("url").getAsString();
                    var v = StreamValidator.validate(url);   // nie ungeprüften Link anzeigen/öffnen
                    if (!v.ok()) continue;
                    out.add(new Entry(o.get("player").getAsString(), v.platform().label, v.normalizedUrl()));
                }
                deliver(cb, out, null);
            } catch (Exception e) {
                deliver(cb, null, "Backend nicht erreichbar: " + e.getClass().getSimpleName());
            }
        });
    }

    // ------------------------------------------------------------ Hinzufügen
    public void add(String player, String link, Callback<Void> cb) {
        if (!StreamValidator.validPlayerName(player)) { deliver(cb, null, "Ungültiger Spielername."); return; }
        var v = StreamValidator.validate(link);
        if (!v.ok()) { deliver(cb, null, v.error()); return; }

        if (!isRemote()) {
            config.localStreams.removeIf(s -> s.player.equalsIgnoreCase(player));
            ModConfig.LocalStream s = new ModConfig.LocalStream();
            s.player = player; s.platform = v.platform().label; s.url = v.normalizedUrl();
            config.localStreams.add(s);
            config.save();
            deliver(cb, null, null);
            return;
        }
        String problem = apiProblem();
        if (problem != null) { deliver(cb, null, problem); return; }
        String own = MinecraftClient.getInstance().getSession().getUsername();
        if (!player.equalsIgnoreCase(own)) { deliver(cb, null, "Online kannst du nur deinen eigenen Eintrag anlegen (" + own + ")."); return; }
        remoteWrite("/streams", player, v.normalizedUrl(), cb);
    }

    // -------------------------------------------------------------- Entfernen
    public void remove(String player, Callback<Void> cb) {
        if (!isRemote()) {
            config.localStreams.removeIf(s -> s.player.equalsIgnoreCase(player));
            config.save();
            deliver(cb, null, null);
            return;
        }
        String problem = apiProblem();
        if (problem != null) { deliver(cb, null, problem); return; }
        String own = MinecraftClient.getInstance().getSession().getUsername();
        if (!player.equalsIgnoreCase(own)) { deliver(cb, null, "Du kannst nur deinen eigenen Eintrag entfernen."); return; }
        remoteWrite("/streams/delete", player, null, cb);
    }

    /** Challenge holen, per Mojang-Sitzung beweisen, dann schreiben. */
    private void remoteWrite(String path, String player, String url, Callback<Void> cb) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> ch = http.send(HttpRequest.newBuilder(URI.create(base() + "/challenge"))
                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (ch.statusCode() != 200) { deliver(cb, null, "Challenge fehlgeschlagen (" + ch.statusCode() + ")."); return; }
                String serverId = JsonParser.parseString(ch.body()).getAsJsonObject().get("serverId").getAsString();

                String authError = MojangAuth.join(serverId);
                if (authError != null) { deliver(cb, null, authError); return; }

                JsonObject body = new JsonObject();
                body.addProperty("player", player);
                body.addProperty("serverId", serverId);
                if (url != null) body.addProperty("url", url);
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) { deliver(cb, null, null); return; }
                String msg = "Server lehnte ab (Status " + r.statusCode() + ").";
                try { msg = JsonParser.parseString(r.body()).getAsJsonObject().get("error").getAsString(); } catch (Exception ignored) {}
                deliver(cb, null, msg);
            } catch (Exception e) {
                deliver(cb, null, "Backend nicht erreichbar: " + e.getClass().getSimpleName());
            }
        });
    }
}
