# Ibis Wallet

Ibis est un portefeuille Bitcoin modulaire en auto-garde, axé sur un design intuitif, la confidentialité et la personnalisation.

Conçu pour les utilisateurs expérimentés : pas de tutoriels ni de petites roues.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obtenir l'APK sur GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Langues :** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · Français · [Deutsch](README.de.md)

## Fonctionnalités principales

### Layer 1 — Bitcoin
- **Multi-Wallet** - Créer, importer, exporter et basculer entre plusieurs portefeuilles
- **Multi-Seed** - Prend en charge les phrases mnémoniques BIP39 ou Electrum à l'import
- **Dice Entropy** - Générer des seeds à partir de lancers de dés
- **Multisig Wallets** - Importer des descripteurs multisig, coordonner les signatures PSBT et signer localement comme cosignataire
- **Watch-only Wallets** - Importer xpub/zpub, descripteurs de sortie ou une adresse unique
- **Import Private Key** - Balayer ou importer des clés privées (format WIF)
- **Silent Payments** - Prise en charge complète BIP-352 : envoi et réception, avec serveur Frigate par défaut
- **Hardware Wallet Signing** - Utiliser des QR codes animés ou des fichiers .psbt pour signer en air-gap
- **Coin Control** - Sélectionner des UTXO précis, geler/dégeler, dépenser depuis des sorties individuelles
- **Require Coin Control** - Option pour forcer la sélection des UTXO avant les envois on-chain
- **RBF & CPFP** - Augmenter les frais des transactions non confirmées, y compris des flux PSBT sûrs en multisig
- **RBF by Default** - Les envois on-chain signalent replace-by-fee par défaut ; désactivable
- **Cancel Transactions** - Annuler les transactions sortantes non confirmées avec RBF
- **Manual Broadcast** - Diffuser toute transaction brute signée directement sur le réseau Bitcoin
- **Batch Sending** - Envoyer à plusieurs destinataires en une seule transaction
- **Message Signing** - Signer et vérifier des messages avec BIP137
- **Checksum Helper** - Calculer le mot de checksum manquant à partir d'un préfixe de 11/23 mots
- **BIP329 Labels** - Étiquettes de portefeuille standard pour les transactions et les adresses
- **Transaction Search** - Rechercher dans l'historique par date, adresse ou étiquette
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning natif, Ark, Spark et Liquid
- **Modular Integration** - Choisir quel Layer 2 activer pour chaque portefeuille
- **Lightning** - Connecter un nœud distant via LND (LND REST), CLN (clnrest) ou NWC (NIP-47)
- **Ark** - Portefeuille Ark complet avec gestion des VTXO, envoi et réception Lightning, boarding payjoin et sorties unilatérales (Built on [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Intégration Spark avec envoi et réception Lightning et on-chain, plus sorties unilatérales (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Portefeuille Liquid complet avec transactions confidentielles et prise en charge USDt (Built on [LWK](https://github.com/Blockstream/lwk))
- **Watch-only Liquid Wallets** - Importer des portefeuilles Liquid en lecture seule avec des descripteurs SLIP77
- **Lightning Payments** - Payer des factures Bolt 11 et Bolt 12, ou des adresses Lightning
- **Lightning Invoices** - Générer des factures Bolt 11
- **Chain Swaps** - Échanger facilement entre L1 et L2
- **Coin Control** - Sélectionner des UTXO précis pour les swaps et les paiements
- **BIP329 Labels** - Étiquettes pour les transactions Ark, Spark et Liquid

### Confidentialité et sécurité
- **Offline by Default** - L'app démarre sans aucune connexion externe
- **Built-in Tor** - Tor natif, sans Orbot ni proxy externe
- **PIN & Biometrics** - Avec délai de verrouillage configurable
- **Duress PIN** - PIN de contrainte qui ouvre un portefeuille leurre
- **Wipe PIN** - Configurer un PIN secondaire qui efface en silence toutes les données du portefeuille sur l'appareil
- **Auto-Wipe** - Seuil d'échecs de déverrouillage qui efface automatiquement et irréversiblement toutes les données de l'app
- **Clear Clipboard** - Option pour vider automatiquement le presse-papiers au verrouillage ou à la fermeture de l'app
- **Cloak Mode** - Déguiser Ibis en app de calculatrice
- **Privacy Toggle** - Masquer tous les montants et soldes
- **Wipe History** - Effacer localement des transactions précises ou tout l'historique
- **Wallet Locks** - Verrouiller des portefeuilles précis de façon indépendante
- **Hardened Metadata** - Logs et erreurs d'UI assainis pour réduire les métadonnées visibles par l'OS

### Connectivité et serveurs
- **Custom Servers** - Te connecter à tes propres serveurs Electrum, explorateur de blocs et estimation des frais
- **NFC Support** - Émettre et recevoir des demandes de paiement par NFC
- **Update Notifications** - Activer ou désactiver les avis de nouvelles versions
- **Bitcoin URI Handling** - S'enregistrer comme gestionnaire des liens `bitcoin:`

### Localisation
- **Languages** - Anglais, russe, espagnol, portugais (Brésil), français et allemand
- **Typeface** - Plusieurs polices de caractères

### Sauvegarde et restauration
- **Full Encrypted Backups** - Sauvegarder et restaurer tout l'état de l'app, y compris portefeuilles, réglages et étiquettes

## Compilation

Nécessite Android Studio avec JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Rapport de couverture
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM** (armeabi-v7a, arm64-v8a)

## Bug Bounty

> [!IMPORTANT]
> **Programme bug bounty suspendu par manque de fonds.** Les rapports sont bienvenus et seront reconnus, mais aucune récompense monétaire ne sera versée jusqu'à la reprise du programme.

Nous offrons $1,000 payés en BTC pour les vulnérabilités critiques d'Ibis Wallet pouvant entraîner une perte non autorisée des fonds de l'utilisateur.

Sont éligibles les vulnérabilités avec une voie d'attaque pratique et réaliste entraînant le vol ou la perte permanente des fonds (p. ex. compromission distante du portefeuille, extraction de la seed, manipulation de transactions, etc.).

Ibis est un logiciel en bêta conçu pour les utilisateurs Bitcoin expérimentés. Les sauvegardes de seed hors appareil sont la pratique standard attendue : les problèmes entièrement récupérables en restaurant depuis la seed, sans vol par un tiers, sont reconnus mais exclus des récompenses.

Les problèmes de moindre gravité, les vulnérabilités théoriques sans exploit pratique, ou les constats ne menant pas à une perte de fonds seront reconnus, mais restent en général hors du champ des récompenses monétaires.

## Avertissement

Ibis est codé et audité par les modèles d'IA de pointe les plus récents.

Audit le plus récent : [15 août 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Dons

Comme j'ai d'abord fait ce portefeuille pour moi, je ne m'attends pas à des dons. Mais les tokens ne sont pas bon marché. Si tu veux montrer ta gratitude, vas-y.

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```

## Licence

Open source. Voir [LICENSE](../LICENSE) pour plus de détails.
