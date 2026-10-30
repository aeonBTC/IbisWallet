# Ibis Wallet

Ibis es una billetera Bitcoin modular de autocustodia, enfocada en un diseño intuitivo, la privacidad y la personalización.

Diseñada para usuarios experimentados: sin tutoriales ni ruedas de entrenamiento.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Obtener APK desde GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Idiomas:** [English](../README.md) · Español · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [日本語](README.ja.md) · [简体中文](README.zh-CN.md)

## Características principales

### Layer 1 — Bitcoin
- **Multi-billetera** - Crear, importar, exportar y cambiar entre varias billeteras
- **Multi-semilla** - Compatible con frases semilla BIP39 o Electrum al importar
- **Entropía por dados** - Genera semillas a partir de tiradas de dados
- **Billeteras multisig** - Importar descriptores multisig, coordinar firmas PSBT y firmar localmente como cosignatario
- **Billeteras de solo observación** - Importar xpub/zpub, descriptores de salida o una sola dirección
- **Importar clave privada** - Barrer o importar claves privadas (formato WIF)
- **Pagos silenciosos** - Soporte completo BIP-352: envío y recepción, con servidor Frigate por defecto
- **Firma con monedero físico** - Usar códigos QR animados o archivos .psbt para firmas air-gapped
- **Firma PSBT por NFC** - Envía PSBTs a un monedero físico con un toque NFC y recibe la firma de la misma forma
- **Firmante sin conexión** - Usa Ibis en un teléfono sin conexión como firmante en frío para PSBT de solo observación.
- **Protección contra dust** - Marca el dust sospechoso y lo excluye de la selección automática
- **Control de monedas** - Seleccionar UTXOs concretos, congelar/descongelar, enviar desde salidas individuales
- **Requerir control de monedas** - Opción para forzar la selección de UTXOs antes de envíos on-chain
- **RBF y CPFP** - Aumentar comisiones en transacciones no confirmadas, incluidos flujos PSBT seguros para multisig
- **RBF por defecto** - Los envíos on-chain marcan replace-by-fee por defecto; se puede desactivar
- **Cancelar transacciones** - Cancelar transacciones salientes no confirmadas con RBF
- **Difusión manual** - Transmitir cualquier transacción firmada en bruto a la red Bitcoin
- **Envío por lotes** - Enviar a varios destinatarios en una sola transacción
- **Firma de mensajes** - Firmar y verificar mensajes con BIP137
- **Asistente de checksum** - Calcular la palabra de checksum faltante desde prefijo de 11/23 palabras
- **Etiquetas BIP329** - Etiquetas de billetera estándar para transacciones y direcciones
- **Búsqueda de transacciones** - Buscar el historial por fecha, dirección o etiqueta
- **Basado en** [BDK](https://bitcoindevkit.org/)

### Layer 2 — Lightning nativo, Ark, Spark y Liquid
- **Integración modular** - Elegir qué Layer 2 activar en cada billetera
- **Lightning** - Conectar un nodo remoto vía LND (LND REST), CLN (clnrest) o NWC (NIP-47)
- **Ark** - Ark L2 con gestión de VTXO, envío y recepción Lightning, boarding en una sola transacción y salidas unilaterales (Basado en [Bark](https://github.com/ark-bitcoin/bark))
- **Spark** - Spark L2 con envío y recepción Lightning y on-chain, y salidas unilaterales (Basado en [Breez-SDK](https://github.com/breez/spark-sdk))
- **Liquid** - Billetera Liquid completa con transacciones confidenciales y soporte USDt (Basado en [LWK](https://github.com/Blockstream/lwk))
- **Billeteras Liquid de solo observación** - Importar billeteras Liquid de solo lectura con descriptores SLIP77
- **Pagos Lightning** - Pagar facturas Bolt 11 y Bolt 12, o direcciones Lightning
- **Facturas Lightning** - Generar facturas Bolt 11
- **Swaps on-chain** - Intercambiar fácilmente entre L1 y L2
- **Control de monedas** - Seleccionar UTXOs concretos para swaps y pagos
- **Etiquetas BIP329** - Etiquetas para transacciones Ark, Spark y Liquid

### Privacidad y seguridad
- **Sin conexión por defecto** - La app arranca sin conexiones externas
- **Tor integrado** - Tor nativo, sin Orbot ni proxies externos
- **PIN y biometría** - Con tiempo de bloqueo configurable
- **PIN de coacción** - PIN de coacción que abre una billetera señuelo
- **PIN de borrado** - Configurar un PIN secundario que borra en silencio todos los datos de billetera del dispositivo
- **Borrado automático** - Umbral de desbloqueos fallidos que borra de forma automática e irreversible todos los datos de la app
- **Vaciar portapapeles** - Opción para vaciar automáticamente el portapapeles al bloquear o cerrar la app
- **Modo camuflaje** - Disfrazar Ibis como una app de calculadora
- **Alternar privacidad** - Ocultar todos los importes y saldos
- **Borrar historial** - Borrar localmente transacciones concretas o todo el historial
- **Bloqueo de billeteras** - Bloquear billeteras concretas de forma independiente
- **Metadatos reforzados** - Logs y errores de UI sanitizados para reducir metadatos visibles al SO

### Conectividad y servidores
- **Servidores personalizados** - Conectar a tus propios servidores Electrum, explorador de bloques y estimación de comisiones
- **Soporte NFC** - Emitir y recibir solicitudes de pago por NFC
- **Notificaciones de actualización** - Activar o desactivar avisos de nuevas versiones
- **Manejo de URI de Bitcoin** - Registrarse como manejador de enlaces `bitcoin:`

### Localización
- **Idiomas** - Inglés, español, ruso, portugués (Brasil), francés, alemán, japonés y chino simplificado
- **Tipografía** - Varias tipografías

### Copia de seguridad y restauración
- **Copias de seguridad cifradas completas** - Respaldar y restaurar todo el estado de la app, incluidas billeteras, ajustes y etiquetas

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

Recompensas del bug bounty: [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Aviso

Ibis está codificado y auditado por los modelos de IA de frontera más actuales.

Auditorías: [29 de octubre de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta) · [15 de agosto de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## Donaciones

Como originalmente hice esta billetera para mí, no espero donaciones. Sin embargo, los tokens no son baratos. Si quieres mostrar agradecimiento, adelante.

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## Licencia

Código abierto. Consulta [LICENSE](../LICENSE) para más detalles.
