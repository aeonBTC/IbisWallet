# Ibis Wallet

Ibis — модульный Bitcoin-кошелёк с самохранением, ориентированный на интуитивный дизайн, приватность и настраиваемость.

Для опытных пользователей — без обучения и «страховки».

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Скачать APK с GitHub" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**Языки:** [English](../README.md) · [Español](README.es.md) · Русский · [Português (Brasil)](README.pt-BR.md)

## Ключевые возможности

### Layer 1 — Bitcoin
- **Multi-Wallet** - Создание, импорт, экспорт и переключение между несколькими кошельками
- **Multi-Seed** - Импорт seed-фраз BIP39 или Electrum
- **Multisig Wallets** - Импорт multisig-дескрипторов, координация подписи PSBT и локальная подпись как со-подписант
- **Watch-only Wallets** - Импорт xpub/zpub, output-дескрипторов или одного адреса
- **Import Private Key** - Свип или импорт приватных ключей (формат WIF)
- **Hardware Wallet Signing** - Анимированные QR-коды или файлы .psbt для air-gapped подписи
- **Coin Control** - Выбор конкретных UTXO, заморозка/разморозка, отправка с отдельных выходов
- **Require Coin Control** - Опция, требующая выбора UTXO перед on-chain отправкой
- **RBF & CPFP** - Повышение комиссии неподтверждённых транзакций, включая безопасные для multisig PSBT-потоки
- **RBF On by Default** - On-chain отправки по умолчанию сигналят replace-by-fee; можно отключить в настройках
- **Cancel Transactions** - Отмена неподтверждённых исходящих транзакций через RBF
- **Manual Broadcast** - Прямая трансляция любой подписанной raw-транзакции в сеть Bitcoin
- **Batch Sending** - Отправка нескольким получателям в одной транзакции
- **Silent Payments** - Отправка на адреса Silent Payment BIP-352
- **Message Signing** - Подпись и проверка сообщений по BIP137
- **BIP329 Labels** - Стандартные метки кошелька для транзакций и адресов
- **Transaction Search** - Поиск истории по дате, адресу или метке
- **Built on** [BDK](https://bitcoindevkit.org/)

### Layer 2 — нативный Lightning, Liquid и Spark (Ark скоро)
- **Modular Integration** - Выбор Layer 2 для каждого кошелька
- **Lightning** - Подключение удалённой ноды через LND (LND REST), CLN (clnrest) или NWC (NIP-47)
- **Liquid** - Полноценный Liquid-кошелёк с конфиденциальными транзакциями (Built on [LWK](https://github.com/Blockstream/lwk))
- **Spark** - Интеграция Spark с отправкой и получением Lightning и on-chain (Built on [Breez-SDK](https://github.com/breez/spark-sdk))
- **Lightning Payments** - Оплата инвойсов Bolt 11 и Bolt 12, а также Lightning-адресов
- **Lightning Invoices** - Создание инвойсов Bolt 11
- **Watch-only Liquid Wallets** - Импорт watch-only Liquid-кошельков через дескрипторы SLIP77
- **Liquid USDt** - Хранение и переводы USDt в Liquid
- **Chain Swaps** - Простой обмен между L1 и L2
- **Coin Control** - Выбор конкретных UTXO для свапов и платежей
- **BIP329 Labels** - Метки для транзакций Liquid и Spark

### Приватность и безопасность
- **Offline by Default** - Запуск без внешних подключений
- **Built-in Tor** - Встроенный Tor, без Orbot и внешних прокси
- **PIN & Biometrics** - С настраиваемым временем блокировки
- **Duress PIN** - PIN принуждения, открывающий кошелёк-приманку
- **Wipe PIN** - Необязательный второй PIN: ввод на экране блокировки молча стирает все данные кошелька на устройстве
- **Auto-Wipe** - Порог неудачных разблокировок с автоматическим и необратимым стиранием всех данных приложения
- **Clear Clipboard** - Опция автоматически очищать буфер обмена при блокировке или закрытии приложения
- **Cloak Mode** - Маскировка Ibis под приложение-калькулятор
- **Privacy Toggle** - Скрытие всех сумм и балансов
- **Wipe History** - Локальное стирание отдельных транзакций или всей истории
- **Wallet Locks** - Независимая блокировка отдельных кошельков
- **Hardened Metadata** - Очищенные логи и UI-ошибки для снижения метаданных, видимых ОС

### Подключение и серверы
- **Custom Servers** - Свои серверы Electrum, block explorer и оценки комиссии
- **NFC Support** - Передача и приём платёжных запросов по NFC
- **Update Notifications** - Уведомления о новых версиях
- **Bitcoin URI Handling** - Обработка ссылок `bitcoin:`

### Локализация
- **Languages** - Английский, русский, испанский и португальский (Бразилия)
- **Typeface** - Несколько вариантов шрифта

### Резервное копирование и восстановление
- **Full Encrypted Backups** - Резервное копирование и восстановление всего состояния приложения, включая кошельки, настройки и метки

## Сборка

Требуется Android Studio с JDK 17.

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # Тесты
./gradlew jacocoUnitTestReport    # Отчёт о покрытии
```

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 36 | **ARM** (armeabi-v7a, arm64-v8a)

## Bug Bounty

Мы выплачиваем $1,000 в BTC за критические уязвимости в Ibis Wallet, которые могут привести к несанкционированной потере средств пользователя.

В scope — уязвимости с практическим реалистичным путём атаки, ведущим к краже или безвозвратной потере средств (например, удалённый компромисс кошелька, извлечение seed, манипуляция транзакциями и т.п.).

Менее серьёзные проблемы, теоретические уязвимости без практического exploit-пути или находки без потери средств будут приняты к сведению, но обычно не оплачиваются.

## Отказ от ответственности

Ibis написан и аудирован самыми актуальными frontier AI-моделями.

Последний аудит: [22 июля 2026](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.6-beta)

## Пожертвования

Изначально я сделал этот кошелёк для себя и не рассчитываю на донаты. Но токены недешёвые. Если хотите выразить благодарность — пожалуйста.

<img width="173" height="170" alt="image" src="https://github.com/user-attachments/assets/ade56e74-dcd4-4543-a908-b62ed343e883" />

```bash
bc1qk54j45l8s20z6glxnt5zuk7efq2qsjj9n44wc8
```

## Лицензия

Open source. Подробности в [LICENSE](../LICENSE).
