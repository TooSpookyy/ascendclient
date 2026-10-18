# ASCEND Client (Fabric, Minecraft 1.21.11)

Reine Client-Mod für den Clan ASCEND auf HugoSMP. Taste **K** öffnet das Menü (in den Steuerungs-Einstellungen änderbar).

## Ehrlicher Status

- **Die JAR wurde noch NICHT kompiliert.** Beim Erstellen waren die Fabric-/Minecraft-Server gesperrt. Die reine Logik (Zahlungsparser, Beträge, Auktion, Clan-Info-Parser, Stream-Links) wurde getestet (59 Tests bestanden). Die Minecraft-Teile (GUI, Tasten, Chat-Events) sind ungetestet und könnten beim ersten Build kleine Compilerfehler zeigen, vor allem durch API-Änderungen in 1.21.11.
- Die Versionen in `gradle.properties` konnten nicht gegen die Fabric-Server geprüft werden. Aktuelle Werte stehen auf https://fabricmc.net/develop/ .

## JAR bauen ohne IntelliJ (GitHub)

1. Auf github.com ein neues Repository anlegen.
2. Den **Inhalt** dieses Ordners hochladen (Add file → Upload files; auch den Ordner `.github`).
3. Reiter **Actions** → „Build ASCEND Client“ → warten.
4. Beim fertigen Lauf unten unter **Artifacts** `ascendclient-jar` herunterladen, entpacken.
5. Bei Fehlern: Fehlermeldung (Log des Schritts „Mod bauen“) zeigen, dann wird sie behoben.

## Installation

1. Minecraft schließen.
2. Fabric Loader für 1.21.11 installieren (https://fabricmc.net/use/).
3. **Fabric API** für 1.21.11 in den `mods`-Ordner legen.
4. `ascendclient-1.0.0.jar` ebenfalls in den `mods`-Ordner.
5. Mit dem Fabric-Profil starten, im Spiel **K** drücken.

## Funktionen

- **ASCEND Discord**: öffnet `https://discord.gg/Rg3MWpDYf7` im Browser (nur bei Klick).
- **Clan Info**: sendet `/clan info` nur beim Öffnen oder über „Aktualisieren“ und wertet die Antwort aus. Das HugoSMP-Format ist unbekannt – alles, was nicht erkannt wird, bleibt „nicht erkannt“ (nichts erfunden). Anpassung: `ClanInfoParser.java`.
- **Livestreams** (Twitch/YouTube, nur HTTPS): Ohne Backend **nur lokal**, wird nicht mit anderen synchronisiert (wird im Menü so angezeigt). Mit Backend gemeinsame Liste, siehe unten.
- **Auktion**: lokale Anzeige für das Item in der Haupthand, Mindestgebot, Dauer 30 s / 1 min / 1:30 / 2 min, Timer, Erkennung von `[HUGOSMP] Du hast $… von NAME erhalten.` (Parser: `HugoPaymentParser.java`).

### Grenzen der Auktion
Eine Zahlung im Chat enthält keinen Bezug zur Auktion. Jede erkannte Zahlung ist deshalb **nur „nicht verifiziert“**. Die Mod reserviert kein Geld/Item, löst keine Zahlung aus und synchronisiert Timer nicht mit anderen Spielern. Dafür wäre Serverunterstützung nötig. Zahlungen unter dem Mindestgebot, vom eigenen Namen oder Duplikate (< 0,4 s) zählen nicht als Gebot.

Es werden nur Server-Systemnachrichten gelesen (`ClientReceiveMessageEvents.GAME`). Sendet HugoSMP Zahlungen als Spieler-Chatpaket, müsste ein weiteres Event ergänzt werden.

## Gemeinsame Streamliste (Backend)

Ordner `backend/`, nur Node.js ≥ 18, keine Abhängigkeiten.

```
cd backend
ALLOWED_PLAYERS="Name1,Name2" node server.js
```

- Hinter einen HTTPS-Reverse-Proxy stellen (z. B. Caddy: `deine-domain.de { reverse_proxy localhost:8080 }`).
- In `config/ascendclient.json` (nach dem ersten Start) `"apiUrl": "https://deine-domain.de"` eintragen.
- Identität: Der Client beweist per Mojang-Sitzung (`joinServer`/`hasJoined`) seinen Namen, niemand kann fremde Einträge überschreiben. Im JAR liegen keine Geheimnisse; das Access-Token verlässt den Client nie.
- Schutz: Link-Prüfung (nur Twitch/YouTube, HTTPS), Rate-Limit pro IP, max. 200 Einträge, einmalige Challenges, optionale Namensliste.
- Nach Änderungen der Mojang-API in `MojangAuth.java` anpassen (einzige versionsabhängige Stelle der Identitätsprüfung).

## Logik-Tests lokal

```
mkdir out && javac -d out src/main/java/de/ascend/client/logic/*.java src/test/java/de/ascend/client/logic/LogicTests.java
java -cp out de.ascend.client.logic.LogicTests
```
