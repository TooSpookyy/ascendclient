package de.ascend.client.logic;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt HugoSMP-Zahlungsnachrichten:
 * "[HUGOSMP] Du hast $30.000.000 von Spieler123 erhalten."
 *
 * Toleriert einen optionalen Zusatz zwischen Betrag und "von" (z. B. ein Suffix hinter dem Betrag).
 * Zum Anpassen an die echte Serverausgabe: nur {@link #PATTERN} ändern.
 */
public final class HugoPaymentParser {

    public record Payment(long amount, String payer, String zusatz) {}

    private static final Pattern PATTERN = Pattern.compile(
        "^\\s*\\[HUGOSMP\\]\\s*Du hast\\s+\\$\\s*(\\d[\\d.,']*\\d|\\d)(.*?)\\s+von\\s+([A-Za-z0-9_.]{1,24})\\s+erhalten\\.?\\s*$",
        Pattern.CASE_INSENSITIVE);

    private HugoPaymentParser() {}

    /** @param plainText Nachricht ohne Formatierungscodes (Text#getString()) */
    public static Optional<Payment> parse(String plainText) {
        if (plainText == null || plainText.length() > 300) return Optional.empty();
        Matcher m = PATTERN.matcher(stripFormatting(plainText));
        if (!m.matches()) return Optional.empty();
        Optional<Long> amount = Money.parse(m.group(1));
        if (amount.isEmpty()) return Optional.empty();
        return Optional.of(new Payment(amount.get(), m.group(3), m.group(2).strip()));
    }

    /** Entfernt Minecraft-Farbcodes (§x), falls noch vorhanden. */
    public static String stripFormatting(String s) {
        return s.replaceAll("§.", "");
    }
}
