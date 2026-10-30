# Ibis Wallet

Ibis ist eine selbstverwahrte modulare Bitcoin-Wallet mit Fokus auf intuitives Design, Privatsphäre und Anpassbarkeit.

Für erfahrene Nutzer konzipiert – ohne Anleitung, ohne Stützräder.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="APK von GitHub holen" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Sprachen:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md) · Deutsch · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

## Hauptfunktionen

### Layer 1 — Bitcoin
- **Multi-Wallet** - Erstellen, importieren, exportieren und wechseln Sie zwischen mehreren Wallets
- **Multi-Seed** - Unterstützt BIP39- oder Electrum-Seed-Phrasen beim Import
- **Würfel-Entropie** - Seeds aus Würfelwürfen erzeugen
- **Multisig-Wallets** - Multisig-Deskriptoren importieren, PSBT-Signierung koordinieren und lokal als Co-Signer signieren
- **Watch-only-Wallets** - xpub/zpub, Output-Deskriptoren oder eine einzelne Adresse importieren
- **Privaten Schlüssel importieren** - Private Schlüssel fegen (sweep) oder importieren (WIF-Format)
- **Stille Zahlungen** - Volle BIP-352-Unterstützung: Senden und Empfangen, mit Frigate-Standardserver
- **Signieren mit Hardware-Wallet** - Animierte QR-Codes oder .psbt-Dateien für Air-gapped-Signierung nutzen
- **PSBT-Signierung per NFC** - PSBTs per NFC-Tap an eine Hardware-Wallet senden und die Signatur auf gleichem Weg zurückerhalten
- **Offline-Signierer** - Ibis auf einem Offline-Telefon als Cold-Storage-Signierer für Watch-only-PSBT.
- **Dust-Schutz** - Markiert verdächtigen Dust und schließt ihn von der Auto-Auswahl aus
- **Coin-Auswahl** - Spezifische UTXOs auswählen, einfrieren/auftauen, von einzelnen Outputs senden
- **Coin-Auswahl erzwingen** - Option, um die UTXO-Auswahl vor On-Chain-Sendungen zu erzwingen
- **RBF und CPFP** - Gebühren bei unbestätigten Transaktionen erhöhen, inklusive Multisig-sicherer PSBT-Fee-Bump-Abläufe
- **RBF standardmäßig** - On-Chain-Sendungen signalisieren standardmäßig Replace-by-Fee; deaktivierbar
- **Transaktionen abbrechen** - Unbestätigte ausgehende Transaktionen per RBF abbrechen
- **Manuelle Übertragung** - Jede signierte Raw-Transaktion direkt ans Bitcoin-Netzwerk senden
- **Batch-Versand** - An mehrere Empfänger in einer einzigen Transaktion senden
- **Nachrichtensignierung** - Nachrichten mit BIP137 signieren und verifizieren
- **Prüfsummenhelfer** - Fehlendes Prüfsummenwort aus 11/23-Wörter-Präfix berechnen
- **BIP329-Labels** - Standard-Wallet-Labels für Transaktionen und Adressen
- **Transaktionssuche** - Transaktionsverlauf nach Datum, Adresse oder Label durchsuchen
- **Basiert auf** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Natives Lightning, Ark, Spark und Liquid
- **Modulare Integration** - Wählen Sie, welches Layer 2 Sie pro Wallet aktivieren
- **Lightning** - Remote-Node via LND (LND REST), CLN (clnrest) oder NWC (NIP-47) anbinden
- **Ark** - Ark L2 mit VTXO-Verwaltung, Lightning-Senden und -Empfangen, Single-Transaction-Boarding und unilateralen Exits (Basiert auf [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 mit Lightning- sowie On-Chain-Senden und -Empfangen, und unilateralen Exits (Basiert auf [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Vollständige Liquid-Wallet mit vertraulichen Transaktionen und USDt-Unterstützung (Basiert auf [LWK](https://github.com/Blockstream/lwk))
- **Liquid-Watch-only-Wallets** - Liquid-Watch-only-Wallets per SLIP77-Deskriptor importieren
- **Lightning-Zahlungen** - Bolt-11- und Bolt-12-Rechnungen oder Lightning-Adressen bezahlen
- **Lightning-Rechnungen** - Bolt-11-Rechnungen erstellen
- **Chain-Swaps** - Einfach zwischen L1 und L2 tauschen
- **Coin-Auswahl** - Spezifische UTXOs für Swaps und Zahlungen auswählen
- **BIP329-Labels** - Label-Unterstützung für Ark-, Spark- und Liquid-Transaktionen

### Privatsphäre und Sicherheit
- **Standardmäßig offline** - Die App startet ohne externe Verbindungen
- **Integriertes Tor** - Natives Tor, ohne Orbot oder externe Proxies
- **PIN und Biometrie** - Mit konfigurierbarer Sperrzeit
- **Duress-PIN** - Duress-PIN konfigurieren, der eine Köder-Wallet öffnet
- **Wipe-PIN** - Sekundären PIN konfigurieren, der alle Wallet-Daten auf dem Gerät still löscht
- **Automatisches Löschen** - Schwelle für fehlgeschlagene Entsperrungen, die alle App-Daten automatisch und unwiderruflich löscht
- **Zwischenablage leeren** - Option, die Zwischenablage beim Sperren oder Schließen der App automatisch zu leeren
- **Tarnmodus** - Ibis als Rechner-App tarnen
- **Datenschutz-Umschalter** - Alle Beträge und Salden ausblenden
- **Verlauf löschen** - Bestimmte Transaktionen oder den gesamten Verlauf lokal löschen
- **Wallet-Sperren** - Einzelne Wallets unabhängig sperren
- **Gehärtete Metadaten** - Bereinigte Logs und UI-Fehler für reduzierte OS-sichtbare Metadaten

### Konnektivität und Server
- **Eigene Server** - Verbinden Sie sich mit eigenen Electrum-, Block-Explorer- und Gebührenschätzungs-Servern
- **NFC-Unterstützung** - Zahlungsaufforderungen per NFC senden und empfangen
- **Update-Benachrichtigungen** - Benachrichtigungen über neue Versionen ein- oder ausschalten
- **Bitcoin-URI-Verarbeitung** - Als Handler für `bitcoin:`-Links registrieren

### Lokalisierung
- **Sprachen** - Englisch, Spanisch, Russisch, Portugiesisch (Brasilien), Französisch, Deutsch, Japanisch und Vereinfachtes Chinesisch
- **Schriftart** - Mehrere Schriftarteinstellungen

### Sicherung und Wiederherstellung
- **Vollständige verschlüsselte Backups** - Gesamten App-Status inklusive Wallets, Einstellungen und Labels sichern und wiederherstellen

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

Bug-Bounty-Prämien: [15. August 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Hinweis

Ibis wird von den aktuellsten Frontier-KI-Modellen codiert und auditiert.

Audits: [29. Oktober 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v5.1-beta) · [15. August 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Spenden

Da ich diese Wallet ursprünglich für mich selbst erstellt habe, erwarte ich keine Spenden. Token sind jedoch nicht günstig. Wenn Sie sich bedanken möchten, nur zu.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Lizenz

Open Source. Siehe [LICENSE](../LICENSE) für Details.
