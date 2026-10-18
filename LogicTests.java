package de.ascend.client.logic;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Eigenständige Tests ohne Test-Framework: java de.ascend.client.logic.LogicTests */
public final class LogicTests {
    private static int failed = 0, passed = 0;

    private static void check(String name, boolean cond) {
        if (cond) passed++; else { failed++; System.out.println("FEHLER: " + name); }
    }

    public static void main(String[] args) {
        // --- Money ---
        check("30.000.000", Money.parse("30.000.000").orElse(-1L) == 30_000_000L);
        check("$30.000.000", Money.parse("$30.000.000").orElse(-1L) == 30_000_000L);
        check("1,000,000", Money.parse("1,000,000").orElse(-1L) == 1_000_000L);
        check("500", Money.parse("500").orElse(-1L) == 500L);
        check("leer", Money.parse("").isEmpty());
        check("negativ", Money.parse("-5").isEmpty());
        check("null", Money.parse(null).isEmpty());
        check("Buchstaben", Money.parse("12abc").isEmpty());
        check("0", Money.parse("0").isEmpty());
        check("falsche Gruppen", Money.parse("1.00.000").isEmpty());
        check("gemischte Trenner", Money.parse("1.000,000").isEmpty());
        check("format", Money.format(30_000_000L).equals("$30.000.000"));
        check("format klein", Money.format(999L).equals("$999"));
        check("format 1000", Money.format(1000L).equals("$1.000"));

        // --- Zahlungsparser ---
        var p = HugoPaymentParser.parse("[HUGOSMP] Du hast $30.000.000 von Spieler123 erhalten.");
        check("payment basic", p.isPresent() && p.get().amount() == 30_000_000L && p.get().payer().equals("Spieler123"));
        var p2 = HugoPaymentParser.parse("[HUGOSMP] Du hast $1.500 von Ab_c erhalten.");
        check("payment klein", p2.isPresent() && p2.get().amount() == 1500 && p2.get().payer().equals("Ab_c"));
        var p3 = HugoPaymentParser.parse("[HUGOSMP] Du hast $30.000.000 (Bonus) von X_Y erhalten.");
        check("payment zusatz", p3.isPresent() && p3.get().amount() == 30_000_000L && p3.get().zusatz().equals("(Bonus)"));
        var p4 = HugoPaymentParser.parse("[HUGOSMP] Du hast $5.000.000M von LangerName_12 erhalten.");
        check("payment suffix", p4.isPresent() && p4.get().amount() == 5_000_000L);
        var p5 = HugoPaymentParser.parse("§a[HUGOSMP] §fDu hast $2.000 von Foo erhalten.");
        check("farbcodes", p5.isPresent() && p5.get().amount() == 2000);
        check("kein praefix", HugoPaymentParser.parse("Du hast $30.000.000 von Foo erhalten.").isEmpty());
        check("spielerchat", HugoPaymentParser.parse("<Foo> [HUGOSMP] hallo").isEmpty());
        check("spielerchat fake", HugoPaymentParser.parse("<Foo> [HUGOSMP] Du hast $5 von Foo erhalten.").isEmpty());
        check("kaputter betrag", HugoPaymentParser.parse("[HUGOSMP] Du hast $1.00.0 von Foo erhalten.").isEmpty());
        check("null", HugoPaymentParser.parse(null).isEmpty());
        check("lang", HugoPaymentParser.parse("x".repeat(1000)).isEmpty());

        // --- Auktion ---
        AtomicLong now = new AtomicLong(1_000_000);
        AuctionManager a = new AuctionManager(now::get);
        check("kein item", a.start("", 0, false, "1.000", AuctionManager.Duration.S30, "Ich").isPresent());
        check("ungültiges min", a.start("Diamant", 1, true, "abc", AuctionManager.Duration.S30, "Ich").isPresent());
        check("start ok", a.start("Diamant", 3, true, "1.000.000", AuctionManager.Duration.M1S30, "Ich").isEmpty());
        check("läuft", a.state() == AuctionManager.State.RUNNING);
        check("timer 90", a.remainingSeconds() == 90 && a.remainingFormatted().equals("01:30"));
        check("kein gebot", !a.hasBid());
        a.onPayment(new HugoPaymentParser.Payment(500_000, "A", ""));
        check("unter min", !a.hasBid());
        a.onPayment(new HugoPaymentParser.Payment(2_000_000, "B", ""));
        check("gebot B", a.hasBid() && a.highestBidder().equals("B") && a.highestAmount() == 2_000_000);
        now.addAndGet(1000);
        a.onPayment(new HugoPaymentParser.Payment(2_000_000, "C", ""));
        check("gleich hoch zählt nicht", a.highestBidder().equals("B"));
        now.addAndGet(1000);
        a.onPayment(new HugoPaymentParser.Payment(3_000_000, "Ich", ""));
        check("selbstzahlung ignoriert", a.highestBidder().equals("B"));
        // Duplikat innerhalb 400 ms
        now.addAndGet(1000);
        boolean first = a.onPayment(new HugoPaymentParser.Payment(4_000_000, "D", ""));
        now.addAndGet(100);
        boolean dup = a.onPayment(new HugoPaymentParser.Payment(4_000_000, "D", ""));
        check("duplikat verworfen", first && !dup && a.payments().size() == 5);
        check("höher D", a.highestBidder().equals("D"));
        check("alle unverifiziert", a.payments().stream().filter(x -> x.countedAsBid()).allMatch(x -> x.note().startsWith("Nicht verifiziert")));
        now.addAndGet(100_000);
        a.tick();
        check("beendet", a.state() == AuctionManager.State.ENDED && a.remainingSeconds() == 0);
        int n = a.payments().size();
        a.onPayment(new HugoPaymentParser.Payment(9_000_000, "Z", ""));
        check("nach ende keine wertung", a.payments().size() == n && a.highestBidder().equals("D"));
        check("neue auktion", a.start("Stab", 1, true, "10", AuctionManager.Duration.S30, "Ich").isEmpty() && !a.hasBid());
        check("vier dauern", AuctionManager.Duration.values().length == 4
            && AuctionManager.Duration.S30.seconds == 30 && AuctionManager.Duration.M1.seconds == 60
            && AuctionManager.Duration.M1S30.seconds == 90 && AuctionManager.Duration.M2.seconds == 120);

        // --- Clan Info ---
        var info = ClanInfoParser.parse(List.of("Clan: ASCEND", "Mitglieder: 14", "Moderatoren: 3", "Co-Owner: 2"));
        check("clan zahlen", info.members() == 14 && info.moderators() == 3 && info.coOwners() == 2);
        var info2 = ClanInfoParser.parse(List.of("Owner: Hugo", "Co-Owner: Anna, Bob", "Moderatoren: Cleo, Dan, Eve", "Mitglieder: Fay, Gus"));
        check("clan listen", info2.coOwners() == 2 && info2.moderators() == 3 && info2.memberRoles().get("Anna").equals("Co-Owner"));
        var none = ClanInfoParser.parse(List.of("Unbekannter Text hier"));
        check("nichts erfunden", !none.hasAnyData() && none.members() == null);

        AtomicLong t = new AtomicLong(0);
        ClanInfoCollector c = new ClanInfoCollector(t::get);
        c.begin();
        c.tick();
        check("loading", c.status() == ClanInfoCollector.Status.LOADING);
        t.addAndGet(10_000);
        c.tick();
        check("timeout", c.status() == ClanInfoCollector.Status.ERROR);
        c.begin();
        c.onServerLine("Mitglieder: 5");
        t.addAndGet(2000);
        c.tick();
        check("done", c.status() == ClanInfoCollector.Status.DONE && c.info().members() == 5);
        c.onServerLine("Mitglieder: 99");
        check("ignoriert nach done", c.info().members() == 5);

        // --- Streams ---
        check("twitch ok", StreamValidator.validate("https://twitch.tv/hugo_stream").ok());
        check("yt ok", StreamValidator.validate("https://www.youtube.com/watch?v=abcdefghijk").ok());
        check("yt kanal", StreamValidator.validate("https://youtube.com/@ascend/live").ok());
        check("http abgelehnt", !StreamValidator.validate("http://twitch.tv/hugo").ok());
        check("fremd abgelehnt", !StreamValidator.validate("https://evil.com/twitch.tv/hugo").ok());
        check("subdomain trick", !StreamValidator.validate("https://twitch.tv.evil.com/hugo").ok());
        check("userinfo trick", !StreamValidator.validate("https://twitch.tv@evil.com/hugo").ok());
        check("leer", !StreamValidator.validate("").ok());
        check("name ok", StreamValidator.validPlayerName("Hugo_1"));
        check("name schlecht", !StreamValidator.validPlayerName("a b"));

        System.out.println(passed + " Tests bestanden, " + failed + " fehlgeschlagen");
        if (failed > 0) System.exit(1);
    }
}
