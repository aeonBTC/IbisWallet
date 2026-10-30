# Ibis Wallet

Ibis est un portefeuille Bitcoin modulaire en auto-garde, axé sur un design intuitif, la confidentialité et la personnalisation.

Conçu pour les utilisateurs expérimentés : pas de tutoriels ni de petites roues.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obtenir l'APK sur GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Langues :** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · Français · [Deutsch](README.de.md) · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

## Fonctionnalités principales

### Layer 1 — Bitcoin
- **Multi-portefeuille** - Créer, importer, exporter et basculer entre plusieurs portefeuilles
- **Multi-seed** - Prend en charge les phrases mnémoniques BIP39 ou Electrum à l'import
- **Entropie par dés** - Générer des seeds à partir de lancers de dés
- **Portefeuilles multisig** - Importer des descripteurs multisig, coordonner les signatures PSBT et signer localement comme cosignataire
- **Portefeuilles en lecture seule** - Importer xpub/zpub, descripteurs de sortie ou une adresse unique
- **Importer une clé privée** - Balayer ou importer des clés privées (format WIF)
- **Paiements silencieux** - Prise en charge complète BIP-352 : envoi et réception, avec serveur Frigate par défaut
- **Signature avec portefeuille matériel** - Utiliser des QR codes animés ou des fichiers .psbt pour signer en air-gap
- **Signature PSBT par NFC** - Envoyez des PSBT vers un portefeuille matériel par toucher NFC et recevez la signature de la même façon
- **Signataire hors ligne** - Utilisez Ibis sur un téléphone hors ligne comme signataire à froid pour les PSBT en lecture seule.
- **Protection contre le dust** - Signale le dust suspect et l'exclut de la sélection auto
- **Contrôle des pièces** - Sélectionner des UTXO précis, geler/dégeler, dépenser depuis des sorties individuelles
- **Contrôle des pièces obligatoire** - Option pour forcer la sélection des UTXO avant les envois on-chain
- **RBF et CPFP** - Augmenter les frais des transactions non confirmées, y compris des flux PSBT sûrs en multisig
- **RBF par défaut** - Les envois on-chain signalent replace-by-fee par défaut ; désactivable
- **Annuler des transactions** - Annuler les transactions sortantes non confirmées avec RBF
- **Diffusion manuelle** - Diffuser toute transaction brute signée directement sur le réseau Bitcoin
- **Envoi par lots** - Envoyer à plusieurs destinataires en une seule transaction
- **Signature de messages** - Signer et vérifier des messages avec BIP137
- **Assistant de checksum** - Calculer le mot de checksum manquant à partir d'un préfixe de 11/23 mots
- **Étiquettes BIP329** - Étiquettes de portefeuille standard pour les transactions et les adresses
- **Recherche de transactions** - Rechercher dans l'historique par date, adresse ou étiquette
- **Basé sur** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning natif, Ark, Spark et Liquid
- **Intégration modulaire** - Choisir quel Layer 2 activer pour chaque portefeuille
- **Lightning** - Connecter un nœud distant via LND (LND REST), CLN (clnrest) ou NWC (NIP-47)
- **Ark** - Ark L2 avec gestion des VTXO, envoi et réception Lightning, boarding en une seule transaction et sorties unilatérales (Basé sur [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 avec envoi et réception Lightning et on-chain, et sorties unilatérales (Basé sur [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Portefeuille Liquid complet avec transactions confidentielles et prise en charge USDt (Basé sur [LWK](https://github.com/Blockstream/lwk))
- **Portefeuilles Liquid en lecture seule** - Importer des portefeuilles Liquid en lecture seule avec des descripteurs SLIP77
- **Paiements Lightning** - Payer des factures Bolt 11 et Bolt 12, ou des adresses Lightning
- **Factures Lightning** - Générer des factures Bolt 11
- **Swaps on-chain** - Échanger facilement entre L1 et L2
- **Contrôle des pièces** - Sélectionner des UTXO précis pour les swaps et les paiements
- **Étiquettes BIP329** - Étiquettes pour les transactions Ark, Spark et Liquid

### Confidentialité et sécurité
- **Hors ligne par défaut** - L'app démarre sans aucune connexion externe
- **Tor intégré** - Tor natif, sans Orbot ni proxy externe
- **PIN et biométrie** - Avec délai de verrouillage configurable
- **PIN de contrainte** - PIN de contrainte qui ouvre un portefeuille leurre
- **PIN d'effacement** - Configurer un PIN secondaire qui efface en silence toutes les données du portefeuille sur l'appareil
- **Effacement automatique** - Seuil d'échecs de déverrouillage qui efface automatiquement et irréversiblement toutes les données de l'app
- **Vider le presse-papiers** - Option pour vider automatiquement le presse-papiers au verrouillage ou à la fermeture de l'app
- **Mode camouflage** - Déguiser Ibis en app de calculatrice
- **Basculer la confidentialité** - Masquer tous les montants et soldes
- **Effacer l'historique** - Effacer localement des transactions précises ou tout l'historique
- **Verrouillage de portefeuilles** - Verrouiller des portefeuilles précis de façon indépendante
- **Métadonnées renforcées** - Logs et erreurs d'UI assainis pour réduire les métadonnées visibles par l'OS

### Connectivité et serveurs
- **Serveurs personnalisés** - Te connecter à tes propres serveurs Electrum, explorateur de blocs et estimation des frais
- **Prise en charge NFC** - Émettre et recevoir des demandes de paiement par NFC
- **Notifications de mise à jour** - Activer ou désactiver les avis de nouvelles versions
- **Gestion des URI Bitcoin** - S'enregistrer comme gestionnaire des liens `bitcoin:`

### Localisation
- **Langues** - Anglais, espagnol, russe, portugais (Brésil), français, allemand, japonais et chinois simplifié
- **Police de caractères** - Plusieurs polices de caractères

### Sauvegarde et restauration
- **Sauvegardes chiffrées complètes** - Sauvegarder et restaurer tout l'état de l'app, y compris portefeuilles, réglages et étiquettes

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

Récompenses du bug bounty : [15 août 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Avertissement

Ibis est codé et audité par les modèles d'IA de pointe les plus récents.

Audits : [29 octobre 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v5.1-beta) · [15 août 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Dons

Comme j'ai d'abord fait ce portefeuille pour moi, je ne m'attends pas à des dons. Mais les tokens ne sont pas bon marché. Si tu veux montrer ta gratitude, vas-y.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Licence

Open source. Voir [LICENSE](../LICENSE) pour plus de détails.
