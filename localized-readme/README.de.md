# Ibis Wallet

Ibis ist eine selbstverwahrte modulare Bitcoin-Wallet mit Fokus auf intuitives Design, Privatsphäre und Anpassbarkeit.

Für erfahrene Nutzer konzipiert – ohne Anleitung, ohne Stützräder.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="APK von GitHub holen" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Sprachen:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md) · Deutsch

## Hauptfunktionen

### Layer 1 — Bitcoin
- **Multi-Wallet** - Erstellen, importieren, exportieren und wechseln Sie zwischen mehreren Wallets
- **Multi-Seed** - Unterstützt BIP39- oder Electrum-Seed-Phrasen beim Import
- **Dice Entropy** - Seeds aus Würfelwürfen erzeugen
- **Multisig Wallets** - Multisig-Deskriptoren importieren, PSBT-Signierung koordinieren und lokal als Co-Signer signieren
- **Watch-only Wallets** - xpub/zpub, Output-Deskriptoren oder eine einzelne Adresse importieren
- **Import Private Key** - Private Schlüssel fegen (sweep) oder importieren (WIF-Format)
- **Silent Payments** - Volle BIP-352-Unterstützung: Senden und Empfangen, mit Frigate-Standardserver
- **Hardware Wallet Signing** - Animierte QR-Codes oder .psbt-Dateien für Air-gapped-Signierung nutzen
- **Coin Control** - Spezifische UTXOs auswählen, einfrieren/auftauen, von einzelnen Outputs senden
- **Require Coin Control** - Option, um die UTXO-Auswahl vor On-Chain-Sendungen zu erzwingen
- **RBF & CPFP** - Gebühren bei unbestätigten Transaktionen erhöhen, inklusive Multisig-sicherer PSBT-Fee-Bump-Abläufe
- **RBF by Default** - On-Chain-Sendungen signalisieren standardmäßig Replace-by-Fee; deaktivierbar
- **Cancel Transactions** - Unbestätigte ausgehende Transaktionen per RBF abbrechen
- **Manual Broadcast** - Jede signierte Raw-Transaktion direkt ans Bitcoin-Netzwerk senden
- **Batch Sending** - An mehrere Empfänger in einer einzigen Transaktion senden
- **Message Signing** - Nachrichten mit BIP137 signieren und verifizieren
- **Checksum Helper** - Fehlendes Prüfsummenwort aus 11/23-Wörter-Präfix berechnen
- **BIP329 Labels** - Standard-Wallet-Labels für Transaktionen und Adressen
- **Transaction Search** - Transaktionsverlauf nach Datum, Adresse oder Label durchsuchen
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Natives Lightning, Ark, Spark und Liquid
- **Modular Integration** - Wählen Sie, welches Layer 2 Sie pro Wallet aktivieren
- **Lightning** - Remote-Node via LND (LND REST), CLN (clnrest) oder NWC (NIP-47) anbinden
- **Ark** - Vollständige Ark-Wallet mit VTXO-Verwaltung, Lightning-Senden und -Empfangen, Payjoin-Boarding und unilateralen Exits (Built on [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark-Integration mit Lightning- sowie On-Chain-Senden und -Empfangen, plus unilaterale Exits (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Vollständige Liquid-Wallet mit vertraulichen Transaktionen und USDt-Unterstützung (Built on [LWK](https://github.com/Blockstream/lwk))
- **Watch-only Liquid Wallets** - Liquid-Watch-only-Wallets per SLIP77-Deskriptor importieren
- **Lightning Payments** - Bolt-11- und Bolt-12-Rechnungen oder Lightning-Adressen bezahlen
- **Lightning Invoices** - Bolt-11-Rechnungen erstellen
- **Chain Swaps** - Einfach zwischen L1 und L2 tauschen
- **Coin Control** - Spezifische UTXOs für Swaps und Zahlungen auswählen
- **BIP329 Labels** - Label-Unterstützung für Ark-, Spark- und Liquid-Transaktionen

### Privatsphäre und Sicherheit
- **Offline by Default** - Die App startet ohne externe Verbindungen
- **Built-in Tor** - Natives Tor, ohne Orbot oder externe Proxies
- **PIN & Biometrics** - Mit konfigurierbarer Sperrzeit
- **Duress PIN** - Duress-PIN konfigurieren, der eine Köder-Wallet öffnet
- **Wipe PIN** - Sekundären PIN konfigurieren, der alle Wallet-Daten auf dem Gerät still löscht
- **Auto-Wipe** - Schwelle für fehlgeschlagene Entsperrungen, die alle App-Daten automatisch und unwiderruflich löscht
- **Clear Clipboard** - Option, die Zwischenablage beim Sperren oder Schließen der App automatisch zu leeren
- **Cloak Mode** - Ibis als Rechner-App tarnen
- **Privacy Toggle** - Alle Beträge und Salden ausblenden
- **Wipe History** - Bestimmte Transaktionen oder den gesamten Verlauf lokal löschen
- **Wallet Locks** - Einzelne Wallets unabhängig sperren
- **Hardened Metadata** - Bereinigte Logs und UI-Fehler für reduzierte OS-sichtbare Metadaten

### Konnektivität und Server
- **Custom Servers** - Verbinden Sie sich mit eigenen Electrum-, Block-Explorer- und Gebührenschätzungs-Servern
- **NFC Support** - Zahlungsaufforderungen per NFC senden und empfangen
- **Update Notifications** - Benachrichtigungen über neue Versionen ein- oder ausschalten
- **Bitcoin URI Handling** - Als Handler für `bitcoin:`-Links registrieren

### Lokalisierung
- **Languages** - Englisch, Russisch, Spanisch, Portugiesisch (Brasilien), Französisch und Deutsch
- **Typeface** - Mehrere Schriftarteinstellungen

### Sicherung und Wiederherstellung
- **Full Encrypted Backups** - Gesamten App-Status inklusive Wallets, Einstellungen und Labels sichern und wiederherstellen

## Kompilierung

Erfordert Android Studio mit JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Abdeckungsbericht
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM** (armeabi-v7a, arm64-v8a)

## Bug Bounty

> [!IMPORTANT]
> **Bug-Bounty-Programm derzeit aus Geldmangel ausgesetzt.** Meldungen sind willkommen und werden anerkannt, aber bis zur Wiederaufnahme des Programms werden keine Geldprämien ausgezahlt.

Wir bieten $1,000 in BTC für kritische Schwachstellen in Ibis Wallet, die zu einem unautorisierten Verlust von Nutzerfonds führen können.

In den Umfang fallen Schwachstellen mit einem praktischen, realistischen Angriffspfad, der zu Diebstahl oder permanentem Fondsverlust führt (z. B. Remote-Kompromittierung der Wallet, Seed-Extraktion, Transaktionsmanipulation usw.).

Ibis ist Beta-Software und für erfahrene Bitcoin-Nutzer gebaut. Off-Geräte-Seed-Backups sind erwartete Standardpraxis: Probleme, die durch Wiederherstellung aus der Seed vollständig behebbar sind, ohne Diebstahl durch Dritte, werden anerkannt, fallen aber nicht unter Prämien.

Probleme geringerer Schwere, theoretische Schwachstellen ohne praktischen Exploit-Pfad oder Funde, die nicht zu Fondsverlust führen, werden anerkannt, fallen aber grundsätzlich nicht unter Geldprämien.

## Hinweis

Ibis wird von den aktuellsten Frontier-KI-Modellen codiert und auditiert.

Letztes Audit: [15. August 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Spenden

Da ich diese Wallet ursprünglich für mich selbst erstellt habe, erwarte ich keine Spenden. Token sind jedoch nicht günstig. Wenn Sie sich bedanken möchten, nur zu.

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```

## Lizenz

Open Source. Siehe [LICENSE](../LICENSE) für Details.
