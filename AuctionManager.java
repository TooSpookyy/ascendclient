package de.ascend.client.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Lokaler Auktionszustand. Reine Anzeige: Sie reserviert weder Geld noch Items und wird nicht
 * mit anderen Spielern synchronisiert. Eine Chat-Zahlung enthält keinen Bezug zur Auktion,
 * daher ist JEDE erkannte Zahlung nur "nicht verifiziert".
 */
public final class AuctionManager {

    public enum Duration {
        S30("30 Sekunden", 30), M1("1 Minute", 60), M1S30("1 Minute 30 Sekunden", 90), M2("2 Minuten", 120);
        public final String label;
        public final int seconds;
        Duration(String label, int seconds) { this.label = label; this.seconds = seconds; }
    }

    public enum State { IDLE, RUNNING, ENDED, CANCELLED }

    public record ReceivedPayment(String payer, long amount, long atMillis, boolean countedAsBid, String note) {}

    private static final long DUPLICATE_WINDOW_MS = 400;

    private final LongSupplier clock;
    private State state = State.IDLE;
    private String itemName = "";
    private int itemCount;
    private long minBid;
    private Duration duration = Duration.S30;
    private long endAtMillis;
    private long highestAmount;
    private String highestBidder;
    private String selfName = "";
    private final List<ReceivedPayment> payments = new ArrayList<>();
    private final Deque<String> recentKeys = new ArrayDeque<>();
    private final Deque<Long> recentTimes = new ArrayDeque<>();

    public AuctionManager(LongSupplier clock) { this.clock = clock; }

    public AuctionManager() { this(System::currentTimeMillis); }

    /** Validiert und startet. Gibt Fehlertext zurück oder empty bei Erfolg. */
    public Optional<String> start(String itemName, int itemCount, boolean hasItem, String minBidInput,
                                  Duration duration, String selfName) {
        if (state == State.RUNNING) return Optional.of("Es läuft bereits eine Auktion.");
        if (!hasItem) return Optional.of("Du hältst kein Item in der Haupthand.");
        if (duration == null) return Optional.of("Bitte eine Dauer auswählen.");
        Optional<Long> min = Money.parse(minBidInput);
        if (min.isEmpty()) return Optional.of("Ungültiges Mindestgebot. Nur positive ganze Zahlen, z. B. 1.000.000.");
        this.itemName = itemName;
        this.itemCount = itemCount;
        this.minBid = min.get();
        this.duration = duration;
        this.selfName = selfName == null ? "" : selfName;
        this.highestAmount = 0;
        this.highestBidder = null;
        this.payments.clear();
        this.recentKeys.clear();
        this.recentTimes.clear();
        this.endAtMillis = clock.getAsLong() + duration.seconds * 1000L;
        this.state = State.RUNNING;
        return Optional.empty();
    }

    /** Muss regelmäßig (z. B. jeden Client-Tick) aufgerufen werden. */
    public void tick() {
        if (state == State.RUNNING && clock.getAsLong() >= endAtMillis) state = State.ENDED;
    }

    public void cancel() {
        if (state == State.RUNNING) state = State.CANCELLED;
    }

    public void reset() {
        if (state != State.RUNNING) state = State.IDLE;
    }

    /**
     * Verarbeitet eine erkannte Zahlung. Doppelte Verarbeitung derselben Chatnachricht
     * (gleicher Zahler+Betrag innerhalb weniger 100 ms) wird verworfen.
     * @return true wenn die Zahlung in die Liste aufgenommen wurde
     */
    public boolean onPayment(HugoPaymentParser.Payment p) {
        tick();
        if (state != State.RUNNING) return false;
        long now = clock.getAsLong();
        String key = p.payer().toLowerCase(Locale.ROOT) + "|" + p.amount();
        while (!recentTimes.isEmpty() && now - recentTimes.peekFirst() > DUPLICATE_WINDOW_MS) {
            recentTimes.pollFirst();
            recentKeys.pollFirst();
        }
        if (recentKeys.contains(key)) return false;
        recentKeys.addLast(key);
        recentTimes.addLast(now);

        boolean counted = false;
        String note;
        if (!selfName.isEmpty() && p.payer().equalsIgnoreCase(selfName)) {
            note = "Zahler ist du selbst – ignoriert";
        } else if (p.amount() < minBid) {
            note = "Unter dem Mindestgebot – kein Gebot";
        } else if (highestBidder != null && p.amount() <= highestAmount) {
            note = "Übertrifft das Höchstgebot nicht";
        } else {
            counted = true;
            highestAmount = p.amount();
            highestBidder = p.payer();
            note = "Nicht verifiziert: Zuordnung zur Auktion nicht beweisbar";
        }
        payments.add(new ReceivedPayment(p.payer(), p.amount(), now, counted, note));
        return true;
    }

    public long remainingSeconds() {
        if (state != State.RUNNING) return 0;
        long ms = endAtMillis - clock.getAsLong();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public String remainingFormatted() {
        long s = remainingSeconds();
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    public State state() { return state; }
    public String itemName() { return itemName; }
    public int itemCount() { return itemCount; }
    public long minBid() { return minBid; }
    public Duration duration() { return duration; }
    public boolean hasBid() { return highestBidder != null; }
    public long highestAmount() { return highestAmount; }
    public String highestBidder() { return highestBidder; }
    public List<ReceivedPayment> payments() { return Collections.unmodifiableList(payments); }

    public String statusText() {
        return switch (state) {
            case IDLE -> "Bereit";
            case RUNNING -> "Auktion läuft (lokal)";
            case ENDED -> "Auktion beendet";
            case CANCELLED -> "Auktion abgebrochen";
        };
    }
}
