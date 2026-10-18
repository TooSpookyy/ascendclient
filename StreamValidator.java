package de.ascend.client.logic;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Akzeptiert ausschließlich HTTPS-Links zu Twitch und YouTube. */
public final class StreamValidator {

    public enum Platform { TWITCH("Twitch"), YOUTUBE("YouTube");
        public final String label;
        Platform(String label) { this.label = label; }
    }

    public record Result(boolean ok, Platform platform, String normalizedUrl, String error) {
        static Result fail(String e) { return new Result(false, null, null, e); }
    }

    private static final Pattern TWITCH_PATH = Pattern.compile("^/[A-Za-z0-9_]{3,25}/?$");
    private static final Pattern YT_PATH = Pattern.compile("^/(watch|live/[A-Za-z0-9_-]{6,20}|@[A-Za-z0-9_.-]{2,40}(/live|/streams)?|channel/[A-Za-z0-9_-]{10,40}(/live)?)/?$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private StreamValidator() {}

    public static boolean validPlayerName(String n) { return n != null && PLAYER_NAME.matcher(n).matches(); }

    public static Result validate(String input) {
        if (input == null || input.isBlank()) return Result.fail("Bitte einen Link eingeben.");
        String s = input.strip();
        if (s.length() > 200) return Result.fail("Link ist zu lang.");
        URI uri;
        try {
            uri = new URI(s);
        } catch (Exception e) {
            return Result.fail("Kein gültiger Link.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return Result.fail("Nur HTTPS-Links sind erlaubt.");
        if (uri.getUserInfo() != null || uri.getPort() != -1) return Result.fail("Link enthält unzulässige Bestandteile.");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();

        if (host.equals("twitch.tv") || host.equals("www.twitch.tv")) {
            if (!TWITCH_PATH.matcher(path).matches()) return Result.fail("Twitch-Link muss auf einen Kanal zeigen (https://twitch.tv/name).");
            return new Result(true, Platform.TWITCH, "https://www.twitch.tv" + path.replaceAll("/$", ""), null);
        }
        if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
            if (!YT_PATH.matcher(path).matches()) return Result.fail("YouTube-Link nicht erkannt (watch, live, @Kanal oder channel).");
            String q = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            if (q.length() > 60) return Result.fail("Link enthält zu viele Parameter.");
            return new Result(true, Platform.YOUTUBE, "https://www.youtube.com" + path.replaceAll("/$", "") + q, null);
        }
        if (host.equals("youtu.be")) {
            if (!path.matches("^/[A-Za-z0-9_-]{6,20}$")) return Result.fail("YouTube-Kurzlink ungültig.");
            return new Result(true, Platform.YOUTUBE, "https://youtu.be" + path, null);
        }
        return Result.fail("Nur Twitch und YouTube werden unterstützt.");
    }
}
