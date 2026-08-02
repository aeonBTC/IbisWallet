

# Ibis Wallet

Ibis es una billetera de Bitcoin modular y de custodia propia, con un enfoque en el diseño intuitivo, la privacidad y la personalización.

Diseñada para usuarios experimentados: sin tutoría, sin rueditas de apoyo.

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

## Características Principales

### Capa 1 — Bitcoin
- **Multibilletera** - Crea, importa, exporta y alterna entre múltiples billeteras
- **Multi-Semilla** - Admite frases semilla BIP39 o Electrum para importar billeteras
- **Billeteras Multisig** - Importa descriptores multisig, coordina la firma de PSBT y firma localmente como cofirmante
- **Billeteras de Solo Lectura** - Importa xpub/zpub, descriptores de salida o una sola dirección
- **Importar Clave Privada** - Realiza un barrido (sweep) o importa claves privadas (formato WIF)
- **Firma con Billetera de Hardware** - Usa códigos QR animados o archivos .psbt para firmar claves en entornos desconectados (air-gapped)
- **Control de Monedas** - Selecciona UTXOs específicos, congela/descongela y envía desde salidas individuales
- **RBF y CPFP** - Aumenta las comisiones en transacciones no confirmadas, incluidos flujos seguros de aumento de comisiones PSBT para multisig
- **Cancelar Transacciones** - Cancela transacciones salientes no confirmadas con RBF
- **Transmisión Manual** - Transmite cualquier transacción cruda firmada directamente a la red Bitcoin
- **Envío por Lotes** - Envía a múltiples destinatarios en una sola transacción
- **Pagos Silenciosos** - Envía a direcciones de Pagos Silenciosos BIP-352
- **Firma de Mensajes** - Firma y verifica mensajes con BIP137
- **Etiquetas BIP329** - Etiquetas de billetera estándar de la industria para transacciones y direcciones
- **Búsqueda de Transacciones** - Busca en el historial de transacciones por fecha, dirección o etiqueta
- **Construida sobre** [BDK](https://bitcoindevkit.org/)

### Capa 2 — Lightning, Liquid y Spark nativos (Ark próximamente)
- **Integración Modular** - Elige qué Capa 2 habilitar para cada billetera
- **Lightning** - Conecta un nodo remoto vía LND (LND REST), CLN (clnrest) o NWC (NIP-47)
- **Liquid** - Billetera Liquid completa con transacciones confidenciales (Construida sobre [LWK](https://github.com/Blockstream/lwk))
- **Spark** - Integración de la billetera Spark con Lightning y envío/recepción en cadena (Construida sobre [Breez-SDK](https://github.com/breez/spark-sdk))
- **Pagos Lightning** - Paga facturas Bolt 11 y Bolt 12, o direcciones Lightning
- **Facturas Lightning** - Genera facturas Bolt 11
- **Billeteras Liquid de Solo Lectura** - Importa billeteras Liquid de solo lectura usando descriptores SLIP77
- **USDt en Liquid** - Almacena y realiza transacciones con USDt en Liquid
- **Intercambios entre Cadenas** - Intercambia fácilmente entre L1 y L2
- **Control de Monedas** - Selecciona UTXOs específicos para intercambios y pagos
- **Etiquetas BIP329** - Soporte de etiquetas para transacciones Liquid y Spark

### Privacidad y Seguridad
- **Desconectado por Defecto** - La aplicación se inicia sin conexiones externas
- **Tor Integrado** - Integración nativa de Tor, sin necesidad de Orbot o proxies externos
- **PIN y Biometría** - Con temporizador de bloqueo configurable
- **PIN de Coacción** - Configura un PIN de emergencia que abre una billetera señuelo
- **Autoborrado** - Establece un límite de intentos fallidos que borra automáticamente e irreversiblemente todos los datos de la app
- **Modo Encubrimiento** - Disfraza Ibis como una aplicación de calculadora
- **Opción de Privacidad** - Oculta todos los montos y saldos de la billetera
- **Borrar Historial** - Borra localmente transacciones específicas de la billetera o todo el historial
- **Bloqueo de Billeteras** - Bloquea billeteras específicas de forma independiente
- **Metadatos Reforzados** - Registros y errores de UI sanitizados para reducir metadatos visibles por el SO

### Conectividad y Servidores
- **Servidores Personalizados** - Conéctate a tus propios servidores Electrum, explorador de bloques y estimación de comisiones
- **Soporte NFC** - Transmite y recibe solicitudes de pago mediante un toque NFC
- **Notificaciones de Actualización** - Activa o desactiva las notificaciones de nuevas versiones
- **Manejo de URI Bitcoin** - Regístrate como controlador para enlaces `bitcoin:`

### Localización
- **Idiomas** - Inglés, Ruso, Español y Portugués (Brasil)
- **Tipografía** - Múltiples opciones de configuración de tipografía

### Copia de Seguridad y Restauración
- **Copias de Seguridad Completas y Cifradas** - Respalda y restaura todo el estado de la app, incluyendo billeteras, configuraciones y etiquetas

## Compilación

Requiere Android Studio con JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Tests
./gradlew jacocoUnitTestReport    # Generate coverage report
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM64 only** (arm64-v8a)

## Recompensa por Vulnerabilidades (Bug Bounty)

Ofrecemos $1,000 pagados en BTC por cualquier vulnerabilidad crítica en Ibis Wallet que pueda llevar a la pérdida no autorizada de fondos de los usuarios.

Están en el alcance de la recompensa las vulnerabilidades que proporcionen una ruta de ataque práctica y realista que resulte en el robo o pérdida permanente de fondos de los usuarios (p. ej., compromiso remoto de la billetera, extracción de semilla, manipulación de transacciones, etc.).

Los problemas de menor severidad, vulnerabilidades teóricas sin una ruta de explotación práctica o hallazgos que no lleven a la pérdida de fondos serán reconocidos, pero generalmente están fuera del alcance para recompensas monetarias.

## Descargo de Responsabilidad

Ibis es codificada y auditada por los modelos de IA de vanguardia.

Auditoría más reciente: [22 de julio de 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.6-beta)

## Donaciones

Como originalmente creé esta billetera para uso personal, no espero donaciones. Sin embargo, los tokens no son baratos. Así que si deseas dar las gracias, por favor hazlo. 

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qk54j45l8s20z6glxnt5zuk7efq2qsjj9n44wc8
```


## Licencia

Código abierto. Consulta [LICENSE](LICENSE) para más detalles.
