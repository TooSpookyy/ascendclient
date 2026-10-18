package de.ascend.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Konfiguration in config/ascendclient.json. Enthält KEINE Geheimnisse. */
public final class ModConfig {

    public static final String DISCORD_URL = "https://discord.gg/Rg3MWpDYf7";

    public static final class LocalStream {
        public String player;
        public String platform;
        public String url;
    }

    /** HTTPS-Adresse des Stream-Backends. Leer = nur lokale Liste (keine Synchronisierung). */
    public String apiUrl = "";
    public List<LocalStream> localStreams = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("ascendclient.json"); }

    public static ModConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ModConfig c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), ModConfig.class);
                if (c != null) {
                    if (c.localStreams == null) c.localStreams = new ArrayList<>();
                    if (c.apiUrl == null) c.apiUrl = "";
                    return c;
                }
            }
        } catch (Exception e) {
            AscendClient.LOGGER.warn("Konfiguration konnte nicht gelesen werden, verwende Standard.", e);
        }
        ModConfig c = new ModConfig();
        c.save();
        return c;
    }

    public synchronized void save() {
        try {
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AscendClient.LOGGER.warn("Konfiguration konnte nicht gespeichert werden.", e);
        }
    }
}
