package de.ascend.client.logic;

import java.util.Optional;

/** Geldbeträge als long (keine Gleitkommazahlen). Format der Anzeige: $30.000.000 */
public final class Money {
    /** Obergrenze gegen Überläufe / absurde Eingaben (999 Billiarden). */
    public static final long MAX = 999_999_999_999_999_999L;

    private Money() {}

    /**
     * Parst einen Betrag mit Tausendertrennzeichen (. , ' oder Leerzeichen).
     * Akzeptiert nur ganze Zahlen. "30.000.000" -> 30000000.
     * Eindeutig ungültig: leer, negativ, Buchstaben, Trennzeichen die keine 3er-Gruppen bilden.
     */
    public static Optional<Long> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.strip();
        if (s.startsWith("$")) s = s.substring(1).strip();
        if (s.isEmpty()) return Optional.empty();
        if (!s.matches("[0-9][0-9.,'  ]*")) return Optional.empty();

        String digitsOnly = s.replaceAll("[.,'  ]", "");
        boolean hasSeparator = !s.equals(digitsOnly);
        if (hasSeparator) {
            // Gültige Tausendergruppen: 1-3 Ziffern, dann nur 3er-Gruppen, immer mit gleichem Trennzeichen
            if (!s.matches("[0-9]{1,3}([.,'  ][0-9]{3})+")) return Optional.empty();
            char sep = s.replaceAll("[0-9]", "").charAt(0);
            if (!s.replaceAll("[0-9]", "").chars().allMatch(c -> c == sep)) return Optional.empty();
        }
        if (digitsOnly.length() > 18) return Optional.empty();
        try {
            long v = Long.parseLong(digitsOnly);
            if (v <= 0 || v > MAX) return Optional.empty();
            return Optional.of(v);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 30000000 -> "$30.000.000" */
    public static String format(long amount) {
        return "$" + formatPlain(amount);
    }

    /** 1000000 -> "1.000.000" */
    public static String formatPlain(long amount) {
        String d = Long.toString(Math.abs(amount));
        StringBuilder sb = new StringBuilder();
        int len = d.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append('.');
            sb.append(d.charAt(i));
        }
        return (amount < 0 ? "-" : "") + sb;
    }
}
