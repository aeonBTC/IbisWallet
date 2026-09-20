# Ibis Wallet

Ibis es una billetera Bitcoin modular de autocustodia, enfocada en un diseño intuitivo, la privacidad y la personalización.

Diseñada para usuarios experimentados: sin tutoriales ni ruedas de entrenamiento.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obtener APK desde GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Idiomas:** [English](../README.md) · Español · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md) · [Deutsch](README.de.md)

## Características principales

### Layer 1 — Bitcoin
- **Multi-Wallet** - Crear, importar, exportar y cambiar entre varias billeteras
- **Multi-Seed** - Compatible con frases semilla BIP39 o Electrum al importar
- **Dice Entropy** - Genera semillas a partir de tiradas de dados
- **Multisig Wallets** - Importar descriptores multisig, coordinar firmas PSBT y firmar localmente como cosignatario
- **Watch-only Wallets** - Importar xpub/zpub, descriptores de salida o una sola dirección
- **Import Private Key** - Barrer o importar claves privadas (formato WIF)
- **Silent Payments** - Soporte completo BIP-352: envío y recepción, con servidor Frigate por defecto
- **Hardware Wallet Signing** - Usar códigos QR animados o archivos .psbt para firmas air-gapped
- **Coin Control** - Seleccionar UTXOs concretos, congelar/descongelar, enviar desde salidas individuales
- **Require Coin Control** - Opción para forzar la selección de UTXOs antes de envíos on-chain
- **RBF & CPFP** - Aumentar comisiones en transacciones no confirmadas, incluidos flujos PSBT seguros para multisig
- **RBF by Default** - Los envíos on-chain marcan replace-by-fee por defecto; se puede desactivar
- **Cancel Transactions** - Cancelar transacciones salientes no confirmadas con RBF
- **Manual Broadcast** - Transmitir cualquier transacción firmada en bruto a la red Bitcoin
- **Batch Sending** - Enviar a varios destinatarios en una sola transacción
- **Message Signing** - Firmar y verificar mensajes con BIP137
- **Checksum Helper** - Calcular la palabra de checksum faltante desde prefijo de 11/23 palabras
- **BIP329 Labels** - Etiquetas de billetera estándar para transacciones y direcciones
- **Transaction Search** - Buscar el historial por fecha, dirección o etiqueta
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning nativo, Ark, Spark y Liquid
- **Modular Integration** - Elegir qué Layer 2 activar en cada billetera
- **Lightning** - Conectar un nodo remoto vía LND (LND REST), CLN (clnrest) o NWC (NIP-47)
- **Ark** - Ark L2 con gestión de VTXO, envío y recepción Lightning, payjoin boarding y salidas unilaterales (Built on [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 con envío y recepción Lightning y on-chain, y salidas unilaterales (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Billetera Liquid completa con transacciones confidenciales y soporte USDt (Built on [LWK](https://github.com/Blockstream/lwk))
- **Watch-only Liquid Wallets** - Importar billeteras Liquid de solo lectura con descriptores SLIP77
- **Lightning Payments** - Pagar facturas Bolt 11 y Bolt 12, o direcciones Lightning
- **Lightning Invoices** - Generar facturas Bolt 11
- **Chain Swaps** - Intercambiar fácilmente entre L1 y L2
- **Coin Control** - Seleccionar UTXOs concretos para swaps y pagos
- **BIP329 Labels** - Etiquetas para transacciones Ark, Spark y Liquid

### Privacidad y seguridad
- **Offline by Default** - La app arranca sin conexiones externas
- **Built-in Tor** - Tor nativo, sin Orbot ni proxies externos
- **PIN & Biometrics** - Con tiempo de bloqueo configurable
- **Duress PIN** - PIN de coacción que abre una billetera señuelo
- **Wipe PIN** - Configurar un PIN secundario que borra en silencio todos los datos de billetera del dispositivo
- **Auto-Wipe** - Umbral de desbloqueos fallidos que borra de forma automática e irreversible todos los datos de la app
- **Clear Clipboard** - Opción para vaciar automáticamente el portapapeles al bloquear o cerrar la app
- **Cloak Mode** - Disfrazar Ibis como una app de calculadora
- **Privacy Toggle** - Ocultar todos los importes y saldos
- **Wipe History** - Borrar localmente transacciones concretas o todo el historial
- **Wallet Locks** - Bloquear billeteras concretas de forma independiente
- **Hardened Metadata** - Logs y errores de UI sanitizados para reducir metadatos visibles al SO

### Conectividad y servidores
- **Custom Servers** - Conectar a tus propios servidores Electrum, explorador de bloques y estimación de comisiones
- **NFC Support** - Emitir y recibir solicitudes de pago por NFC
- **Update Notifications** - Activar o desactivar avisos de nuevas versiones
- **Bitcoin URI Handling** - Registrarse como manejador de enlaces `bitcoin:`

### Localización
- **Languages** - Inglés, ruso, español, portugués (Brasil), francés y alemán
- **Typeface** - Varias tipografías

### Copia de seguridad y restauración
- **Full Encrypted Backups** - Respaldar y restaurar todo el estado de la app, incluidas billeteras, ajustes y etiquetas

## Compilación

Requiere Android Studio con JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Informe de cobertura
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM** (armeabi-v7a, arm64-v8a)

## Bug Bounty

> [!IMPORTANT]
> **Programa bug bounty suspendido por falta de fondos.** Los reportes son bienvenidos y se reconocerán, pero no se pagarán recompensas monetarias hasta que el programa se reanude.

Ofrecemos $1,000 pagados en BTC por vulnerabilidades críticas en Ibis Wallet que puedan provocar la pérdida no autorizada de fondos del usuario.

Están en el alcance vulnerabilidades con una vía de ataque práctica y realista que resulte en robo o pérdida permanente de fondos (p. ej. compromiso remoto de la billetera, extracción de la seed, manipulación de transacciones, etc.).

Ibis es software en beta y está diseñado para usuarios de Bitcoin con experiencia. Las copias de la seed fuera del dispositivo son la práctica estándar esperada: los problemas totalmente recuperables restaurando desde la seed, sin robo de terceros, se reconocen pero quedan fuera de las recompensas.

Problemas de menor gravedad, vulnerabilidades teóricas sin exploit práctico, o hallazgos que no conduzcan a pérdida de fondos se reconocerán, pero en general quedan fuera del alcance de recompensas monetarias.

## Aviso

Ibis está codificado y auditado por los modelos de IA de frontera más actuales.

Auditoría más reciente: [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Donaciones

Como originalmente hice esta billetera para mí, no espero donaciones. Sin embargo, los tokens no son baratos. Si quieres mostrar agradecimiento, adelante.

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```

## Licencia

Código abierto. Consulta [LICENSE](../LICENSE) para más detalles.
