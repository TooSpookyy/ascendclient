package de.ascend.client.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erweiterbarer Parser für die Antwort auf "/clan info".
 * Das echte HugoSMP-Format ist nicht bekannt – darum werden gängige Muster erkannt und NICHTS erfunden:
 * Felder, die nicht gefunden werden, bleiben null / leer.
 *
 * Anpassen: die Patterns unten ändern oder ergänzen.
 */
public final class ClanInfoParser {

    public record ClanInfo(Integer members, Integer moderators, Integer coOwners,
                           Map<String, String> memberRoles, List<String> rawLines) {
        public boolean hasAnyData() {
            return members != null || moderators != null || coOwners != null || !memberRoles.isEmpty();
        }
    }

    // Zahl-Zeilen: "Mitglieder: 12", "Members (12)", "Moderatoren: 3"
    private static final Pattern MEMBERS = Pattern.compile("(?i)\\b(mitglieder|members|member)\\b[^\\d\\n]{0,12}(\\d{1,5})");
    private static final Pattern MODS = Pattern.compile("(?i)\\b(moderatoren|moderators|moderator|mods)\\b[^\\d\\n]{0,12}(\\d{1,5})");
    private static final Pattern COOWNERS = Pattern.compile("(?i)\\b(co-?owner[s]?|co-?besitzer|vize|vizeleiter)\\b[^\\d\\n]{0,12}(\\d{1,5})");

    // Namenslisten: "Moderatoren: a, b, c" / "Co-Owner: x"
    private static final Pattern ROLE_LIST = Pattern.compile(
        "(?i)^\\s*(?:\\[[^\\]]*\\]\\s*)?(owner|besitzer|leiter|co-?owner[s]?|co-?besitzer|moderatoren|moderators|moderator|mods|mitglieder|members|member)\\s*[:\\-]\\s*(.+)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{2,24}");

    private ClanInfoParser() {}

    public static ClanInfo parse(List<String> lines) {
        Integer members = null, mods = null, coOwners = null;
        Map<String, String> roles = new LinkedHashMap<>();
        List<String> raw = new ArrayList<>();

        for (String line : lines) {
            String l = HugoPaymentParser.stripFormatting(line).strip();
            if (l.isEmpty()) continue;
            raw.add(l);

            Matcher m;
            if ((m = MEMBERS.matcher(l)).find() && members == null) members = Integer.valueOf(m.group(2));
            if ((m = MODS.matcher(l)).find() && mods == null) mods = Integer.valueOf(m.group(2));
            if ((m = COOWNERS.matcher(l)).find() && coOwners == null) coOwners = Integer.valueOf(m.group(2));

            Matcher rl = ROLE_LIST.matcher(l);
            if (rl.matches()) {
                String role = normalizeRole(rl.group(1));
                for (String part : rl.group(2).split("[,;]")) {
                    String name = part.strip().replaceAll("^[\\[(<]|[\\])>]$", "");
                    if (NAME.matcher(name).matches() && !name.matches("\\d+")) roles.putIfAbsent(name, role);
                }
            }
        }

        // Zahlen NUR ableiten, wenn Namenslisten vorliegen und keine Zahl explizit genannt wurde.
        if (!roles.isEmpty()) {
            if (members == null) members = roles.size();
            if (mods == null) mods = countRole(roles, "Moderator");
            if (coOwners == null) coOwners = countRole(roles, "Co-Owner");
        }
        return new ClanInfo(members, mods, coOwners, roles, raw);
    }

    private static Integer countRole(Map<String, String> roles, String role) {
        int c = 0;
        for (String r : roles.values()) if (r.equals(role)) c++;
        return c;
    }

    private static String normalizeRole(String r) {
        String x = r.toLowerCase(Locale.ROOT);
        if (x.startsWith("co")) return "Co-Owner";
        if (x.startsWith("mod")) return "Moderator";
        if (x.equals("owner") || x.equals("besitzer") || x.equals("leiter")) return "Owner";
        return "Mitglied";
    }

    public static Optional<ClanInfo> parseOrEmpty(List<String> lines) {
        ClanInfo info = parse(lines);
        return info.hasAnyData() ? Optional.of(info) : Optional.empty();
    }
}
