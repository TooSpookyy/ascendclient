package de.ascend.client.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Sammelt Chatzeilen NACH einer bewusst gesendeten "/clan info"-Anfrage.
 * Nur Zeilen innerhalb des Fensters werden berücksichtigt; nach kurzer Ruhe gilt die Antwort als vollständig.
 */
public final class ClanInfoCollector {

    public enum Status { IDLE, LOADING, DONE, ERROR }

    private static final long TIMEOUT_MS = 8000;
    private static final long IDLE_MS = 1200;

    private final LongSupplier clock;
    private Status status = Status.IDLE;
    private long requestedAt;
    private long lastLineAt;
    private final List<String> lines = new ArrayList<>();
    private ClanInfoParser.ClanInfo info;
    private String error = "";
    private long receivedAt;

    public ClanInfoCollector(LongSupplier clock) { this.clock = clock; }

    public ClanInfoCollector() { this(System::currentTimeMillis); }

    public void begin() {
        status = Status.LOADING;
        requestedAt = clock.getAsLong();
        lastLineAt = 0;
        lines.clear();
        error = "";
    }

    /** Nur Server-Systemnachrichten übergeben, keine Spielerchats. */
    public void onServerLine(String plain) {
        if (status != Status.LOADING || plain == null) return;
        if (plain.length() > 500) return;
        lines.add(plain);
        lastLineAt = clock.getAsLong();
        // Zwischenergebnis, damit der Screen sofort etwas zeigen kann
    }

    public void tick() {
        if (status != Status.LOADING) return;
        long now = clock.getAsLong();
        boolean idle = lastLineAt != 0 && now - lastLineAt >= IDLE_MS;
        boolean timeout = now - requestedAt >= TIMEOUT_MS;
        if (!idle && !timeout) return;

        var parsed = ClanInfoParser.parseOrEmpty(lines);
        if (parsed.isPresent()) {
            info = parsed.get();
            receivedAt = now;
            status = Status.DONE;
        } else {
            status = Status.ERROR;
            error = lines.isEmpty()
                ? "Keine Antwort vom Server erhalten (Zeitüberschreitung)."
                : "Antwort erhalten, aber das Format wurde nicht erkannt. Parser in ClanInfoParser anpassen.";
        }
    }

    public Status status() { return status; }
    public String error() { return error; }
    public ClanInfoParser.ClanInfo info() { return info; }
    public List<String> rawLines() { return lines; }
    public long receivedAt() { return receivedAt; }

    /** Alter der letzten gültigen Info in Sekunden, -1 falls keine. */
    public long ageSeconds() {
        return info == null ? -1 : (clock.getAsLong() - receivedAt) / 1000;
    }

    public boolean isStale() { return info != null && ageSeconds() > 60; }
}
